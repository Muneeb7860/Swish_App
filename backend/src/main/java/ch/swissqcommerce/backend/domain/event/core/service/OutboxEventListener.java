package ch.swissqcommerce.backend.domain.event.core.service;

import ch.swissqcommerce.backend.domain.event.core.model.OutboxEventSavedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);
    private final OutboxEventScheduler outboxEventScheduler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOutboxEventSaved(OutboxEventSavedEvent event) {
        log.info("OutboxEventListener: Received OutboxEventSavedEvent for event id={}, dispatching immediately after commit.", 
                event.getEvent().getId());
        try {
            outboxEventScheduler.dispatchEventImmediately(event.getEvent());
        } catch (Exception e) {
            log.error("OutboxEventListener: Failed to dispatch outbox event id={} immediately. Falling back to scheduler. Error: {}", 
                    event.getEvent().getId(), e.getMessage(), e);
        }
    }
}
