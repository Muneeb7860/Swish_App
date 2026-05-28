package ch.swissqcommerce.backend.domain.reward.port.out;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import java.util.Optional;

public interface RewardOutPort {
    Optional<RewardPoints> findRewardPointsByCustomerId(String customerId);
    void saveRewardPoints(RewardPoints rewardPoints);
}
