package ch.swissqcommerce.backend.config;

import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Helper component executing transactional outbox write operations
 * in an isolated new transaction (REQUIRES_NEW) so that security audit logs
 * are persisted even if the calling business transaction rolls back.
 */
@Component
public class SecurityOutboxWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityOutboxWriter.class);
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public SecurityOutboxWriter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeToOutbox(String eventType, String operator, Map<String, Object> details) {
        try {
            String payload = objectMapper.writeValueAsString(details);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("SECURITY")
                    .aggregateId(operator)
                    .eventType(eventType)
                    .payload(payload)
                    .status("PENDING")
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.info("Security Audit logged to Outbox (REQUIRES_NEW): action={}, operator={}", eventType, operator);
        } catch (Exception e) {
            log.error("Failed to write security audit log to outbox database: {}", e.getMessage(), e);
        }
    }
}
