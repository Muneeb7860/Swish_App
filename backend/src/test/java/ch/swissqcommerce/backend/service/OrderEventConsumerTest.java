package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.swissqcommerce.backend.domain.event.adapter.in.kafka.OrderEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderEventConsumer}. Verifies event processing, counter increments, and
 * error handling.
 */
class OrderEventConsumerTest {

    private OrderEventConsumer consumer;
    private SimpleMeterRegistry meterRegistry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        consumer = new OrderEventConsumer(objectMapper, meterRegistry);
    }

    @Test
    void shouldProcessOrderPlacedEventSuccessfully() {
        String payload = "{\"orderId\":\"order-123\",\"customerId\":\"cust-456\",\"total\":42.50}";
        byte[] correlationId = "corr-001".getBytes();
        byte[] eventType = "order.placed".getBytes();

        assertDoesNotThrow(() -> consumer.consume(payload, correlationId, eventType, "order-123"));

        double processed =
                meterRegistry
                        .counter("kafka_consumer_events_processed_total", "topic", "order.events")
                        .count();
        assertEquals(
                1.0, processed, "Processed counter should be incremented after successful event");
    }

    @Test
    void shouldProcessOrderDeliveredEventSuccessfully() {
        String payload = "{\"orderId\":\"order-789\",\"deliveredAt\":\"2026-06-13T10:00:00Z\"}";
        byte[] correlationId = "corr-002".getBytes();
        byte[] eventType = "order.delivered".getBytes();

        assertDoesNotThrow(() -> consumer.consume(payload, correlationId, eventType, "order-789"));

        double processed =
                meterRegistry
                        .counter("kafka_consumer_events_processed_total", "topic", "order.events")
                        .count();
        assertEquals(1.0, processed);
    }

    @Test
    void shouldProcessOrderCancelledEventSuccessfully() {
        String payload = "{\"orderId\":\"order-101\",\"reason\":\"Customer request\"}";
        byte[] eventType = "order.cancelled".getBytes();

        assertDoesNotThrow(() -> consumer.consume(payload, null, eventType, "order-101"));

        double processed =
                meterRegistry
                        .counter("kafka_consumer_events_processed_total", "topic", "order.events")
                        .count();
        assertEquals(1.0, processed);
    }

    @Test
    void shouldHandleUnknownEventTypeGracefully() {
        String payload = "{\"orderId\":\"order-999\"}";
        byte[] eventType = "order.unknown_type".getBytes();

        assertDoesNotThrow(() -> consumer.consume(payload, null, eventType, "order-999"));

        double processed =
                meterRegistry
                        .counter("kafka_consumer_events_processed_total", "topic", "order.events")
                        .count();
        assertEquals(1.0, processed, "Unknown event types should still be counted as processed");
    }

    @Test
    void shouldIncrementFailureCounterOnMalformedPayload() {
        String invalidJson = "not valid json {{{";
        byte[] eventType = "order.placed".getBytes();

        assertThrows(
                RuntimeException.class,
                () -> consumer.consume(invalidJson, null, eventType, "order-bad"));

        double failures =
                meterRegistry
                        .counter("kafka_consumer_processing_errors_total", "topic", "order.events")
                        .count();
        assertEquals(1.0, failures, "Failure counter should be incremented on malformed payload");
    }

    @Test
    void shouldHandleNullCorrelationIdHeader() {
        String payload = "{\"orderId\":\"order-null-corr\"}";
        byte[] eventType = "order.placed".getBytes();

        // null correlation ID should default to "unknown" — no NPE
        assertDoesNotThrow(() -> consumer.consume(payload, null, eventType, "order-null-corr"));
    }

    @Test
    void shouldHandleNullEventTypeHeader() {
        String payload = "{\"orderId\":\"order-null-type\"}";

        // null event type should default to "unknown" — no NPE
        assertDoesNotThrow(() -> consumer.consume(payload, null, null, "order-null-type"));
    }
}
