package ch.swissqcommerce.backend.domain.event.core.service;

import ch.swissqcommerce.backend.config.TelemetrySchemaRegistry;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional Outbox event processor with intelligent topic routing,
 * retry counting, and dead-letter escalation.
 *
 * <p>Routes each outbox event to the correct Kafka topic based on its
 * {@code eventType} field. After {@value #MAX_RETRIES} failed attempts
 * an event is marked FAILED and will no longer be retried.</p>
 *
 * <p>Every dispatched message carries an {@code X-Correlation-ID} header
 * for distributed tracing via MDC + Jaeger.</p>
 */
@Service
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);
    private static final int MAX_RETRIES = 3;

    /** Maps event-type prefixes to their target Kafka topics. */
    private static final Map<String, String> TOPIC_ROUTING = Map.ofEntries(
            // Payment ecosystem
            Map.entry("payment.authorized",          "payment.events"),
            Map.entry("payment.captured",            "payment.events"),
            Map.entry("payment.failed",              "payment.events"),
            Map.entry("payment.refunded",            "payment.events"),
            Map.entry("payment.fraud_check",         "fraud.detection.request"),
            Map.entry("payment.notification",        "notification.send"),
            Map.entry("payment.compensation",        "payment.compensation"),

            // Balance / Account
            Map.entry("balance.check.request",       "balance.check.request"),
            Map.entry("balance.check.response",      "balance.check.response"),
            Map.entry("account.update",              "account.update"),

            // Transaction
            Map.entry("transaction.request",         "transaction.request"),

            // Gateway
            Map.entry("payment.gateway.request",     "payment.gateway.request"),
            Map.entry("gateway.response",            "gateway.response"),

            // Security
            Map.entry("security.audit.log",          "security.audit.log"),
            Map.entry("security.jwt.anomaly",        "security.jwt.anomaly"),
            Map.entry("security.vault.rotation",     "security.vault.rotation"),
            Map.entry("security.compliance.check",   "security.compliance.check"),
            Map.entry("security.anomaly",            "security.jwt.anomaly"),
            Map.entry("admin.chaos.inject",          "security.audit.log"),

            // Enrollment
            Map.entry("enrollment.state_change",     "enrollment.state_change"),

            // Order lifecycle (legacy / fallback)
            Map.entry("order.placed",                "order.events"),
            Map.entry("order.delivered",             "order.events"),
            Map.entry("order.cancelled",             "order.events")
    );

    private static final String DEFAULT_TOPIC = "order.events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TelemetrySchemaRegistry telemetrySchemaRegistry;

    public OutboxEventScheduler(OutboxEventRepository outboxEventRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                TelemetrySchemaRegistry telemetrySchemaRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.telemetrySchemaRegistry = telemetrySchemaRegistry;
    }

    @Scheduled(fixedDelay = 4000)
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findAll().stream()
                .filter(e -> "PENDING".equalsIgnoreCase(e.getStatus()))
                .toList();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox Scheduler: Found {} pending events to dispatch.", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            dispatchEvent(event);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void dispatchEventImmediately(OutboxEvent event) {
        if (event == null || event.getId() == null) return;
        outboxEventRepository.findById(event.getId()).ifPresent(freshEvent -> {
            if ("PENDING".equalsIgnoreCase(freshEvent.getStatus())) {
                dispatchEvent(freshEvent);
            }
        });
    }

    private void dispatchEvent(OutboxEvent event) {
        String targetTopic = resolveTopicForEvent(event.getEventType());
        String correlationId = UUID.randomUUID().toString();

        try {
            // Perform schema validation before publishing
            telemetrySchemaRegistry.validate(event.getEventType(), event.getPayload());
        } catch (IllegalArgumentException valEx) {
            log.error("Outbox Schema Validation [REJECTED]: event id={}, eventType='{}' failed validation: {}. Payload: {}",
                    event.getId(), event.getEventType(), valEx.getMessage(), event.getPayload());
            event.setStatus("FAILED");
            outboxEventRepository.save(event);
            return; // Reject event from publishing
        }

        try {
            MDC.put("correlationId", correlationId);

            Message<String> message = MessageBuilder
                    .withPayload(event.getPayload())
                    .setHeader(KafkaHeaders.TOPIC, targetTopic)
                    .setHeader(KafkaHeaders.KEY, event.getAggregateId())
                    .setHeader("X-Correlation-ID", correlationId.getBytes(StandardCharsets.UTF_8))
                    .setHeader("X-Event-Type", event.getEventType().getBytes(StandardCharsets.UTF_8))
                    .setHeader("X-Aggregate-Type", event.getAggregateType().getBytes(StandardCharsets.UTF_8))
                    .build();

            kafkaTemplate.send(message);

            event.setStatus("PUBLISHED");
            outboxEventRepository.save(event);

            log.info("Outbox [PUBLISHED]: topic='{}', eventType='{}', aggregateId='{}', correlationId='{}'",
                    targetTopic, event.getEventType(), event.getAggregateId(), correlationId);

        } catch (Exception ex) {
            int retryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
            retryCount++;

            if (retryCount >= MAX_RETRIES) {
                event.setStatus("FAILED");
                log.error("Outbox [FAILED]: event id={} exhausted {} retries. Marked FAILED. Error: {}",
                        event.getId(), MAX_RETRIES, ex.getMessage(), ex);
            } else {
                log.warn("Outbox [RETRY {}/{}]: event id={} will retry on next poll. Error: {}",
                        retryCount, MAX_RETRIES, event.getId(), ex.getMessage());
            }

            event.setRetryCount(retryCount);
            outboxEventRepository.save(event);
        } finally {
            MDC.remove("correlationId");
        }
    }

    /**
     * Resolves the Kafka topic for a given event type.
     * Falls back to {@value #DEFAULT_TOPIC} for unrecognized event types.
     */
    String resolveTopicForEvent(String eventType) {
        if (eventType == null) {
            return DEFAULT_TOPIC;
        }
        return TOPIC_ROUTING.getOrDefault(eventType, DEFAULT_TOPIC);
    }
}
