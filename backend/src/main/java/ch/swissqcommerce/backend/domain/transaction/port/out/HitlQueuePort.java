package ch.swissqcommerce.backend.domain.transaction.port.out;

import ch.swissqcommerce.backend.model.HitlQueue;

public interface HitlQueuePort {
    HitlQueue save(HitlQueue queue);
}
