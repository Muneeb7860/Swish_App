package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class OutboxEncryptionTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void testOutboxEventPayloadEncryptionAndDecryption() {
        // 1. Create a raw event with a sensitive payload
        String originalPayload = "{\"secret_key\": \"super-secret-123\", \"user_email\": \"customer@example.com\"}";
        
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId("order-999")
                .eventType("OrderCreated")
                .payload(originalPayload)
                .status("PENDING")
                .build();

        // 2. Save the event via JPA
        OutboxEvent savedEvent = outboxEventRepository.saveAndFlush(event);
        assertNotNull(savedEvent.getId());

        // Clear the persistence context to force reloading from the database
        entityManager.clear();

        // 3. Query the raw database column using native SQL (bypasses JPA entity converter)
        String rawDbValue = (String) entityManager.createNativeQuery(
                "SELECT payload FROM oltp.outbox_events WHERE id = :id"
        ).setParameter("id", savedEvent.getId()).getSingleResult();

        // Assert that the raw value stored in the database is encrypted (not equal to original plaintext)
        assertNotNull(rawDbValue);
        assertNotEquals(originalPayload, rawDbValue);
        
        // Assert it is a Base64-like string (standard output for our AES converter)
        assertTrue(rawDbValue.length() > 0);
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(rawDbValue));

        // 4. Retrieve via JPA repository (triggers entity converter decryption)
        OutboxEvent retrievedEvent = outboxEventRepository.findById(savedEvent.getId()).orElse(null);
        assertNotNull(retrievedEvent);
        
        // Assert that the retrieved value is automatically decrypted back to the original plaintext
        assertEquals(originalPayload, retrievedEvent.getPayload());
    }
}
