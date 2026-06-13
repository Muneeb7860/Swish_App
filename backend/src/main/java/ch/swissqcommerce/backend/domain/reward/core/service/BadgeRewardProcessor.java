package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import org.springframework.stereotype.Component;

@Component
public class BadgeRewardProcessor implements RewardProcessor {

    private final RewardOutPort rewardOutPort;

    public BadgeRewardProcessor(RewardOutPort rewardOutPort) {
        this.rewardOutPort = rewardOutPort;
    }

    @Override
    public RewardType getType() {
        return RewardType.BADGE;
    }

    @Override
    public void process(String customerId, int amount, String description) {
        rewardOutPort
                .findCustomerById(customerId)
                .ifPresent(
                        customer -> {
                            int newScore = Math.min(100, customer.getTrustScore() + amount);
                            customer.setTrustScore(newScore);
                            if (customer.getConsecutiveOrdersCompleted() >= 5) {
                                customer.setVipStatus(true);
                            }
                            rewardOutPort.saveCustomer(customer);
                        });
    }
}
