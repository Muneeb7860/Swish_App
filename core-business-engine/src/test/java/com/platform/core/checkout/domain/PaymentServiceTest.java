package com.platform.core.checkout.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.checkout.adapters.PaymentRepository;
import com.platform.core.common.OutboxEntity;
import com.platform.core.common.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    public void testProcessCheckoutPayment_NewPayment() {
        String idempotencyKey = "key-123";
        String customerId = "cust-1";
        String orderId = "order-1";
        BigDecimal amount = new BigDecimal("100.00");

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        Payment result = paymentService.processCheckoutPayment(idempotencyKey, customerId, orderId, amount);

        assertNotNull(result);
        assertEquals(PaymentStatus.INITIATED, result.getStatus());

        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEntity savedOutbox = outboxCaptor.getValue();
        assertEquals("Payment", savedOutbox.getAggregateType());
        assertEquals("PaymentInitiated", savedOutbox.getType());
        assertTrue(savedOutbox.getPayload().contains("\"status\":\"INITIATED\""));
    }

    @Test
    public void testProcessCheckoutPayment_Idempotent() {
        String idempotencyKey = "key-123";
        Payment existingPayment = new Payment(idempotencyKey, "c1", "o1", BigDecimal.TEN, PaymentStatus.INITIATED);
        
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.processCheckoutPayment(idempotencyKey, "c1", "o1", BigDecimal.TEN);

        assertEquals(existingPayment, result);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(outboxRepository, never()).save(any(OutboxEntity.class));
    }

    @Test
    public void testConfirmPayment() {
        String intentId = "pi_12345";
        
        paymentService.confirmPayment(intentId);

        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEntity savedOutbox = outboxCaptor.getValue();
        assertEquals("Payment", savedOutbox.getAggregateType());
        assertEquals("PaymentConfirmed", savedOutbox.getType());
        assertEquals(intentId, savedOutbox.getAggregateId());
        assertTrue(savedOutbox.getPayload().contains("\"status\":\"CONFIRMED\""));
    }

    @Test
    public void testFailPayment() {
        String intentId = "pi_999";
        
        paymentService.failPayment(intentId);

        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEntity savedOutbox = outboxCaptor.getValue();
        assertEquals("Payment", savedOutbox.getAggregateType());
        assertEquals("PaymentFailed", savedOutbox.getType());
        assertEquals(intentId, savedOutbox.getAggregateId());
        assertTrue(savedOutbox.getPayload().contains("\"status\":\"FAILED\""));
    }
}
