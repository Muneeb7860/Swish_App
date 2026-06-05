package ch.swissqcommerce.backend.domain.reward.port.out;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import java.util.Optional;
import java.util.List;

public interface RewardOutPort {
    Optional<RewardPoints> findRewardPointsByCustomerId(String customerId);
    void saveRewardPoints(RewardPoints rewardPoints);
    void saveLoyaltyRecord(CustomerLoyalty loyalty);
    List<CustomerLoyalty> findLoyaltyRecordsByCustomerId(String customerId);
}
