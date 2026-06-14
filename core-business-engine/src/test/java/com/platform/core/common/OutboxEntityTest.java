package com.platform.core.common;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class OutboxEntityTest {

    @Test
    public void testConstructorsAndGetters() {
        OutboxEntity entity = new OutboxEntity("Type", "ID", "Event", "Payload");
        assertNotNull(entity.getId());
        assertEquals("Type", entity.getAggregateType());
        assertEquals("ID", entity.getAggregateId());
        assertEquals("Event", entity.getType());
        assertEquals("Payload", entity.getPayload());
        assertNotNull(entity.getCreatedAt());
        assertNull(entity.getProcessedAt());

        UUID newId = UUID.randomUUID();
        Instant newTime = Instant.now();
        
        entity.setId(newId);
        entity.setAggregateType("T2");
        entity.setAggregateId("I2");
        entity.setType("E2");
        entity.setPayload("P2");
        entity.setCreatedAt(newTime);
        entity.setProcessedAt(newTime);

        assertEquals(newId, entity.getId());
        assertEquals("T2", entity.getAggregateType());
        assertEquals("I2", entity.getAggregateId());
        assertEquals("E2", entity.getType());
        assertEquals("P2", entity.getPayload());
        assertEquals(newTime, entity.getCreatedAt());
        assertEquals(newTime, entity.getProcessedAt());
    }

    @Test
    public void testAesEncryptionConverter() {
        AesEncryptionConverter converter = new AesEncryptionConverter();
        String original = "{\"amount\":100.00,\"customerId\":\"cust-1\"}";
        String encrypted = converter.convertToDatabaseColumn(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
        
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
