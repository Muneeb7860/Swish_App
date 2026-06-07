package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PointsRewardProcessor {
    public RewardPoints calculatePointsForOrder(String customerId, String orderId, BigDecimal amount) {
        return null; // mocked
    }

    public CustomerLoyalty updateCustomerTier(String customerId, RewardPoints points) {
        return null; // mocked
    }
}
