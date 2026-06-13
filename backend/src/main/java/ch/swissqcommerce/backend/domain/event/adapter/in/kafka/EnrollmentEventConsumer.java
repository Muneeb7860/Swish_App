package ch.swissqcommerce.backend.domain.event.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code enrollment.state_change} topic.
 *
 * <p>Handles rider and merchant onboarding state transitions published by the Outbox Scheduler.
 * Enables reactive workflow triggers when enrollment milestones are reached (e.g. L1→L2→L3
 * validation).
 */
@Component
public class EnrollmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentEventConsumer.class);
    private final ObjectMapper objectMapper;
    private final Counter enrollmentEventsProcessed;
    private final Counter enrollmentEventsFailures;

    public EnrollmentEventConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.enrollmentEventsProcessed =
                Counter.builder("kafka_consumer_events_processed_total")
                        .tag("topic", "enrollment.state_change")
                        .description("Total enrollment events successfully processed")
                        .register(meterRegistry);
        this.enrollmentEventsFailures =
                Counter.builder("kafka_consumer_processing_errors_total")
                        .tag("topic", "enrollment.state_change")
                        .description("Total enrollment event processing failures")
                        .register(meterRegistry);
    }

    @KafkaListener(
            topics = "enrollment.state_change",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(name = "X-Correlation-ID", required = false) byte[] correlationIdBytes,
            @Header(name = "X-Event-Type", required = false) byte[] eventTypeBytes,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId) {
        String correlationId =
                correlationIdBytes != null ? new String(correlationIdBytes) : "unknown";
        String eventType = eventTypeBytes != null ? new String(eventTypeBytes) : "unknown";

        try {
            MDC.put("correlationId", correlationId);
            log.info(
                    "EnrollmentEventConsumer: Received event [type={}, aggregateId={},"
                            + " correlationId={}]",
                    eventType,
                    aggregateId,
                    correlationId);

            JsonNode eventData = objectMapper.readTree(payload);
            processEnrollmentEvent(eventType, aggregateId, eventData);
            enrollmentEventsProcessed.increment();

            log.info(
                    "EnrollmentEventConsumer: Successfully processed event [type={},"
                            + " aggregateId={}]",
                    eventType,
                    aggregateId);
        } catch (Exception e) {
            enrollmentEventsFailures.increment();
            log.error(
                    "EnrollmentEventConsumer: Failed to process event [type={}, aggregateId={},"
                            + " correlationId={}]: {}",
                    eventType,
                    aggregateId,
                    correlationId,
                    e.getMessage(),
                    e);
            throw new RuntimeException("Enrollment event processing failed", e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void processEnrollmentEvent(String eventType, String aggregateId, JsonNode eventData) {
        String newState = eventData.path("newState").asText("unknown");
        String enrollmentType = eventData.path("enrollmentType").asText("unknown");

        log.info(
                "EnrollmentEventConsumer: State change — type={}, aggregateId={},"
                        + " enrollmentType={}, newState={}",
                eventType,
                aggregateId,
                enrollmentType,
                newState);

        // Future: trigger onboarding workflow steps (document verification, background checks)
        // based on the new state. E.g., L1_VERIFIED → schedule L2 review.
    }
}
