package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import org.springframework.stereotype.Component;

@Component
public class PointsRewardProcessor implements RewardProcessor {

    private final RewardOutPort rewardOutPort;

    public PointsRewardProcessor(RewardOutPort rewardOutPort) {
        this.rewardOutPort = rewardOutPort;
    }

    @Override
    public RewardType getType() {
        return RewardType.POINTS;
    }

    @Override
    public void process(String customerId, int amount, String description) {
        RewardPoints points = rewardOutPort.findRewardPointsByCustomerId(customerId)
                .orElseGet(() -> {
                    RewardPoints newPoints = new RewardPoints();
                    newPoints.setCustomerId(customerId);
                    newPoints.setLoyaltyPoints(0);
                    return newPoints;
                });
        points.setLoyaltyPoints(points.getLoyaltyPoints() + amount);
        rewardOutPort.saveRewardPoints(points);

        CustomerLoyalty log = new CustomerLoyalty();
        log.setCustomerId(customerId);
        log.setPointsChanged(amount);
        log.setDescription(description);
        rewardOutPort.saveLoyaltyRecord(log);
    }
}
