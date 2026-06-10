package com.platform.core.checkout.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class WholesaleOrderEvaluatedConsumerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private WholesaleOrderEvaluatedConsumer consumerConfig;

    @Test
    public void testProcessEvaluatedOrder_SuccessUpdate() {
        Consumer<Map<String, Object>> consumer = consumerConfig.processEvaluatedOrder();

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", "order-123");
        payload.put("status", "APPROVED");
        payload.put("ai_reasoning", "Good credit");
        payload.put("credit_limit_remaining", 5000.0);

        when(jdbcTemplate.update(anyString(), eq("APPROVED"), eq("Good credit"), eq(5000.0), eq("order-123")))
                .thenReturn(1);

        consumer.accept(payload);

        verify(jdbcTemplate).update(anyString(), eq("APPROVED"), eq("Good credit"), eq(5000.0), eq("order-123"));
    }

    @Test
    public void testProcessEvaluatedOrder_NotFound() {
        Consumer<Map<String, Object>> consumer = consumerConfig.processEvaluatedOrder();

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", "order-999");
        payload.put("status", "REJECTED");
        payload.put("ai_reasoning", "Poor credit");
        payload.put("credit_limit_remaining", 0.0);

        when(jdbcTemplate.update(anyString(), eq("REJECTED"), eq("Poor credit"), eq(0.0), eq("order-999")))
                .thenReturn(0);

        consumer.accept(payload);

        verify(jdbcTemplate).update(anyString(), eq("REJECTED"), eq("Poor credit"), eq(0.0), eq("order-999"));
    }
}
