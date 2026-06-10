package com.platform.core.checkout.domain;

import com.platform.core.common.OutboxEntity;
import com.platform.core.common.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class B2bOrderServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private B2bOrderService b2bOrderService;

    @Test
    public void testPlaceWholesaleOrder_WithPii() {
        String customerId = "cust-1";
        BigDecimal amount = new BigDecimal("500.00");

        String orderId = b2bOrderService.placeWholesaleOrder(customerId, amount, true);

        assertNotNull(orderId);
        
        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        
        OutboxEntity savedEvent = outboxCaptor.getValue();
        assertEquals("WholesaleOrder", savedEvent.getAggregateType());
        assertEquals("WholesaleOrderPlaced", savedEvent.getType());
        assertEquals(orderId, savedEvent.getAggregateId());
        
        String payload = savedEvent.getPayload();
        assertTrue(payload.contains("\"pii_flag\": true"));
        assertTrue(payload.contains(orderId));
        assertTrue(payload.contains(customerId));
    }

    @Test
    public void testPlaceWholesaleOrder_WithoutPii() {
        String customerId = "cust-2";
        BigDecimal amount = new BigDecimal("250.00");

        String orderId = b2bOrderService.placeWholesaleOrder(customerId, amount, false);

        assertNotNull(orderId);
        
        ArgumentCaptor<OutboxEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        
        OutboxEntity savedEvent = outboxCaptor.getValue();
        assertEquals("WholesaleOrder", savedEvent.getAggregateType());
        assertEquals("WholesaleOrderPlaced", savedEvent.getType());
        
        String payload = savedEvent.getPayload();
        assertTrue(payload.contains("\"pii_flag\": false"));
        assertTrue(payload.contains(orderId));
        assertTrue(payload.contains(customerId));
    }
}
