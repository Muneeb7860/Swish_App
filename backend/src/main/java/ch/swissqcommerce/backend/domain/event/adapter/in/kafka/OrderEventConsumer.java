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
 * Kafka consumer for the {@code order.events} topic.
 *
 * <p>Handles order lifecycle events (placed, delivered, cancelled) published by the Outbox
 * Scheduler and closes the event loop by logging, correlating, and triggering downstream actions
 * (e.g. notification dispatch).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final ObjectMapper objectMapper;
    private final Counter orderEventsProcessed;
    private final Counter orderEventsFailures;

    public OrderEventConsumer(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.orderEventsProcessed =
                Counter.builder("kafka_consumer_events_processed_total")
                        .tag("topic", "order.events")
                        .description("Total order events successfully processed")
                        .register(meterRegistry);
        this.orderEventsFailures =
                Counter.builder("kafka_consumer_processing_errors_total")
                        .tag("topic", "order.events")
                        .description("Total order event processing failures")
                        .register(meterRegistry);
    }

    @KafkaListener(topics = "order.events", groupId = "${spring.kafka.consumer.group-id}")
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
                    "OrderEventConsumer: Received event [type={}, aggregateId={},"
                            + " correlationId={}]",
                    eventType,
                    aggregateId,
                    correlationId);

            JsonNode eventData = objectMapper.readTree(payload);
            processOrderEvent(eventType, aggregateId, eventData);
            orderEventsProcessed.increment();

            log.info(
                    "OrderEventConsumer: Successfully processed event [type={}, aggregateId={}]",
                    eventType,
                    aggregateId);
        } catch (Exception e) {
            orderEventsFailures.increment();
            log.error(
                    "OrderEventConsumer: Failed to process event [type={}, aggregateId={},"
                            + " correlationId={}]: {}",
                    eventType,
                    aggregateId,
                    correlationId,
                    e.getMessage(),
                    e);
            throw new RuntimeException("Order event processing failed", e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void processOrderEvent(String eventType, String aggregateId, JsonNode eventData) {
        switch (eventType) {
            case "order.placed":
                log.info(
                        "OrderEventConsumer: Order PLACED — aggregateId={}, data={}",
                        aggregateId,
                        eventData);
                // Future: trigger inventory reservation, notification dispatch
                break;
            case "order.delivered":
                log.info(
                        "OrderEventConsumer: Order DELIVERED — aggregateId={}, data={}",
                        aggregateId,
                        eventData);
                // Future: trigger delivery confirmation notification
                break;
            case "order.cancelled":
                log.info(
                        "OrderEventConsumer: Order CANCELLED — aggregateId={}, data={}",
                        aggregateId,
                        eventData);
                // Future: trigger refund workflow, inventory release
                break;
            default:
                log.warn(
                        "OrderEventConsumer: Unknown order event type '{}' — skipping.", eventType);
        }
    }
}
