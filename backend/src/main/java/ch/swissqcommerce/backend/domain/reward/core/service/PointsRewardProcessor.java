package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        RewardPoints points =
                rewardOutPort
                        .findRewardPointsByCustomerId(customerId)
                        .orElseGet(
                                () ->
                                        RewardPoints.builder()
                                                .customerId(customerId)
                                                .loyaltyPoints(0)
                                                .build());

        points.setLoyaltyPoints(points.getLoyaltyPoints() + amount);
        rewardOutPort.saveRewardPoints(points);

        CustomerLoyalty record =
                CustomerLoyalty.builder()
                        .customerId(customerId)
                        .pointsChanged(amount)
                        .description(description)
                        .createdAt(OffsetDateTime.now())
                        .build();
        rewardOutPort.saveLoyaltyRecord(record);
    }

    public RewardPoints calculatePointsForOrder(
            String customerId, String orderId, BigDecimal amount) {
        return null; // legacy/mocked
    }

    public CustomerLoyalty updateCustomerTier(String customerId, RewardPoints points) {
        return null; // legacy/mocked
    }
}
