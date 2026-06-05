package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;

public interface RewardProcessor {
    RewardType getType();
    void process(String customerId, int amount, String description);
}
