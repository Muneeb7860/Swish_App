package com.platform.core.checkout.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Consumer;

@Configuration
public class WholesaleOrderEvaluatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(WholesaleOrderEvaluatedConsumer.class);
    private final JdbcTemplate jdbcTemplate;

    public WholesaleOrderEvaluatedConsumer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Spring Cloud Stream Kafka Consumer for AI Evaluation Results.
     * Binding: b2b-wholesale-in-0
     */
    @Bean
    @Transactional
    public Consumer<Map<String, Object>> processEvaluatedOrder() {
        return payload -> {
            String orderId = (String) payload.get("order_id");
            String status = (String) payload.get("status");
            String aiReasoning = (String) payload.get("ai_reasoning");
            Double creditLimitRemaining = (Double) payload.get("credit_limit_remaining");

            log.info("Received AI Evaluation for Order ID: {} with Status: {}", orderId, status);

            // Update the Database with the AI Decision
            String sql = """
                    UPDATE wholesale_orders 
                    SET ai_status = ?, 
                        ai_reasoning = ?, 
                        credit_limit_remaining = ?, 
                        evaluated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                    """;
            
            int rows = jdbcTemplate.update(sql, status, aiReasoning, creditLimitRemaining, orderId);

            if (rows > 0) {
                log.info("Successfully updated order {} to {}", orderId, status);
            } else {
                log.warn("Order {} not found in database for update. It may have been placed asynchronously and not committed yet.", orderId);
            }
        };
    }
}
