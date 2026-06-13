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
 * Kafka consumer for the {@code payment.events} topic.
 *
 * <p>Handles payment lifecycle events (authorized, captured, failed, refunded)
 * published by the Outbox Scheduler. Correlates payment state transitions
 * with their parent orders via the aggregate ID.</p>
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final ObjectMapper objectMapper;
    private final Counter paymentEventsProcessed;
    private final Counter paymentEventsFailures;

    public PaymentEventConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.paymentEventsProcessed = Counter.builder("kafka_consumer_events_processed_total")
                .tag("topic", "payment.events")
                .description("Total payment events successfully processed")
                .register(meterRegistry);
        this.paymentEventsFailures = Counter.builder("kafka_consumer_processing_errors_total")
                .tag("topic", "payment.events")
                .description("Total payment event processing failures")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "payment.events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(
            @Payload String payload,
            @Header(name = "X-Correlation-ID", required = false) byte[] correlationIdBytes,
            @Header(name = "X-Event-Type", required = false) byte[] eventTypeBytes,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId
    ) {
        String correlationId = correlationIdBytes != null ? new String(correlationIdBytes) : "unknown";
        String eventType = eventTypeBytes != null ? new String(eventTypeBytes) : "unknown";

        try {
            MDC.put("correlationId", correlationId);
            log.info("PaymentEventConsumer: Received event [type={}, aggregateId={}, correlationId={}]",
                    eventType, aggregateId, correlationId);

            JsonNode eventData = objectMapper.readTree(payload);
            processPaymentEvent(eventType, aggregateId, eventData);
            paymentEventsProcessed.increment();

            log.info("PaymentEventConsumer: Successfully processed event [type={}, aggregateId={}]",
                    eventType, aggregateId);
        } catch (Exception e) {
            paymentEventsFailures.increment();
            log.error("PaymentEventConsumer: Failed to process event [type={}, aggregateId={}, correlationId={}]: {}",
                    eventType, aggregateId, correlationId, e.getMessage(), e);
            throw new RuntimeException("Payment event processing failed", e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void processPaymentEvent(String eventType, String aggregateId, JsonNode eventData) {
        switch (eventType) {
            case "payment.authorized":
                log.info("PaymentEventConsumer: Payment AUTHORIZED — aggregateId={}, amount={}",
                        aggregateId, eventData.path("amount"));
                // Future: update order status to PAYMENT_AUTHORIZED
                break;
            case "payment.captured":
                log.info("PaymentEventConsumer: Payment CAPTURED — aggregateId={}, amount={}",
                        aggregateId, eventData.path("amount"));
                // Future: confirm order fulfilment, notify customer
                break;
            case "payment.failed":
                log.warn("PaymentEventConsumer: Payment FAILED — aggregateId={}, reason={}",
                        aggregateId, eventData.path("reason"));
                // Future: cancel order, notify customer, trigger retry/HITL
                break;
            case "payment.refunded":
                log.info("PaymentEventConsumer: Payment REFUNDED — aggregateId={}, amount={}",
                        aggregateId, eventData.path("amount"));
                // Future: update ledger, notify customer
                break;
            default:
                log.warn("PaymentEventConsumer: Unknown payment event type '{}' — skipping.", eventType);
        }
    }
}
