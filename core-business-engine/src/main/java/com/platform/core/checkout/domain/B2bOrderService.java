package com.platform.core.checkout.domain;

import com.platform.core.common.OutboxEntity;
import com.platform.core.common.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class B2bOrderService {

    private final OutboxRepository outboxRepository;

    public B2bOrderService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Places a B2B Wholesale Order.
     * Guaranteed Dual-Write safety via the Transactional Outbox pattern.
     */
    @Transactional
    public String placeWholesaleOrder(String customerId, BigDecimal totalAmount, boolean containsPii) {
        String orderId = UUID.randomUUID().toString();

        // In a real application, you would persist the Order Entity to PostgreSQL here.
        // For this MVP execution phase, we simulate the database save.

        // Construct the Outbox JSON Payload matching the Avro schema precisely.
        // The 'pii_flag' triggers the n8n Hybrid Router (Local Ollama vs Groq API)
        String payload = String.format("""
                {
                    "order_id": "%s",
                    "customer_id": "%s",
                    "total_amount": %f,
                    "currency": "INR",
                    "pii_flag": %b,
                    "items": [],
                    "placed_at": %d
                }
                """, orderId, customerId, totalAmount, containsPii, Instant.now().toEpochMilli());

        // Save the Outbox Event in the EXACT SAME Postgres Transaction
        OutboxEntity outboxEvent = new OutboxEntity(
                "WholesaleOrder", 
                orderId, 
                "WholesaleOrderPlaced", 
                payload
        );
        outboxRepository.save(outboxEvent);

        return orderId;
    }
}
