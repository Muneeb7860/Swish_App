package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.model.OutboxEvent;

public interface OutboxEventPort {
    OutboxEvent save(OutboxEvent event);
}
