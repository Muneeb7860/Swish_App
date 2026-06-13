package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.event.adapter.in.web.WebhookController;
import ch.swissqcommerce.backend.domain.event.adapter.out.persistence.DomainEventEntity;
import ch.swissqcommerce.backend.domain.event.port.in.EventUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebhookController}.
 * Verifies HMAC validation, payload parsing, and event persistence.
 */
class WebhookControllerTest {

    private static final String TEST_SECRET = "test-webhook-secret";
    private WebhookController controller;
    private EventUseCase eventUseCase;
    private SimpleMeterRegistry meterRegistry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        eventUseCase = mock(EventUseCase.class);
        controller = new WebhookController(eventUseCase, objectMapper, TEST_SECRET, meterRegistry);
    }

    private String computeHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void shouldAcceptValidWebhookWithCorrectSignature() throws Exception {
        String body = "{\"eventType\":\"order.placed\",\"aggregateId\":\"order-123\",\"payload\":{\"item\":\"coffee\"}}";
        String signature = computeHmac(body);

        DomainEventEntity savedEvent = DomainEventEntity.builder()
                .eventId("evt-001")
                .eventType("order.placed")
                .status("PENDING")
                .build();
        when(eventUseCase.publishEvent(anyString(), anyString())).thenReturn(savedEvent);

        ResponseEntity<Map<String, String>> response = controller.receiveN8nWebhook(signature, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("accepted", response.getBody().get("status"));
        assertEquals("evt-001", response.getBody().get("eventId"));
        verify(eventUseCase).publishEvent(eq("order.placed"), anyString());
    }

    @Test
    void shouldRejectWebhookWithMissingSignature() {
        String body = "{\"eventType\":\"order.placed\"}";

        ResponseEntity<Map<String, String>> response = controller.receiveN8nWebhook(null, body);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("Missing"));
        verify(eventUseCase, never()).publishEvent(anyString(), anyString());
    }

    @Test
    void shouldRejectWebhookWithBlankSignature() {
        String body = "{\"eventType\":\"order.placed\"}";

        ResponseEntity<Map<String, String>> response = controller.receiveN8nWebhook("  ", body);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(eventUseCase, never()).publishEvent(anyString(), anyString());
    }

    @Test
    void shouldRejectWebhookWithInvalidSignature() {
        String body = "{\"eventType\":\"order.placed\"}";
        String invalidSignature = "deadbeef1234567890abcdef";

        ResponseEntity<Map<String, String>> response = controller.receiveN8nWebhook(invalidSignature, body);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("Invalid"));
        verify(eventUseCase, never()).publishEvent(anyString(), anyString());
    }

    @Test
    void shouldRejectWebhookWithMissingEventType() throws Exception {
        String body = "{\"aggregateId\":\"order-123\",\"payload\":{}}";
        String signature = computeHmac(body);

        ResponseEntity<Map<String, String>> response = controller.receiveN8nWebhook(signature, body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("eventType"));
        verify(eventUseCase, never()).publishEvent(anyString(), anyString());
    }

    @Test
    void shouldHandleMissingPayloadField() throws Exception {
        String body = "{\"eventType\":\"enrollment.state_change\",\"aggregateId\":\"rider-001\"}";
        String signature = computeHmac(body);

        DomainEventEntity savedEvent = DomainEventEntity.builder()
                .eventId("evt-002")
                .eventType("enrollment.state_change")
                .status("PENDING")
                .build();
        when(eventUseCase.publishEvent(anyString(), anyString())).thenReturn(savedEvent);

        ResponseEntity<Map<String, String>> response = controller.receiveN8nWebhook(signature, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // When payload is missing, it should default to "{}"
        verify(eventUseCase).publishEvent(eq("enrollment.state_change"), eq("{}"));
    }

    @Test
    void shouldIncrementReceivedCounterOnSuccess() throws Exception {
        String body = "{\"eventType\":\"test.event\",\"aggregateId\":\"agg-1\",\"payload\":{}}";
        String signature = computeHmac(body);

        DomainEventEntity savedEvent = DomainEventEntity.builder()
                .eventId("evt-003")
                .eventType("test.event")
                .status("PENDING")
                .build();
        when(eventUseCase.publishEvent(anyString(), anyString())).thenReturn(savedEvent);

        controller.receiveN8nWebhook(signature, body);

        double received = meterRegistry.counter("webhook_events_received_total").count();
        assertEquals(1.0, received);
    }

    @Test
    void shouldIncrementRejectedCounterOnAuthFailure() {
        String body = "{\"eventType\":\"order.placed\"}";
        controller.receiveN8nWebhook(null, body);
        controller.receiveN8nWebhook("invalid-sig", body);

        double rejected = meterRegistry.counter("webhook_events_rejected_total").count();
        assertEquals(2.0, rejected, "Both missing and invalid signatures should increment rejected counter");
    }

    @Test
    void verifyHmacReturnsTrueForCorrectSignature() throws Exception {
        String body = "test payload";
        String signature = computeHmac(body);
        assertTrue(controller.verifyHmac(body, signature));
    }

    @Test
    void verifyHmacReturnsFalseForIncorrectSignature() {
        assertFalse(controller.verifyHmac("test payload", "wrong-signature"));
    }
}
