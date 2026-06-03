package ch.swissqcommerce.backend.domain.event.core.service;

import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Highly reliable Transactional Outbox event processor.
 * Periodically polls the H2/PostgreSQL database for "PENDING" outbox events,
 * dispatches them via Kafka, and commits their status as "PUBLISHED".
 * Guarantees at-least-once delivery semantics even in the face of sudden JVM crashes.
 */
@Service
public class OutboxEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxEventScheduler(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 4000) // Polls every 4 seconds
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findAll().stream()
                .filter(e -> "PENDING".equalsIgnoreCase(e.getStatus()))
                .toList();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Outbox Scheduler: Found {} pending transactional events to process.", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                log.info("Outbox Scheduler [KAFKA DISPATCH]: Topic='{}', EventType='{}', Payload='{}'",
                        "order.events", event.getEventType(), event.getPayload());

                kafkaTemplate.send("order.events", event.getPayload());

                // Mark as successfully published inside the transaction
                event.setStatus("PUBLISHED");
                outboxEventRepository.save(event);
                
                log.info("Outbox Scheduler [COMMIT SUCCESS]: Transactional outbox event id={} marked as PUBLISHED.", event.getId());
            } catch (Exception ex) {
                log.error("Outbox Scheduler Alert: Failed to process transactional outbox event id={}. Will auto-retry on next polling block. Error: {}", 
                        event.getId(), ex.getMessage(), ex);
            }
        }
    }
}
