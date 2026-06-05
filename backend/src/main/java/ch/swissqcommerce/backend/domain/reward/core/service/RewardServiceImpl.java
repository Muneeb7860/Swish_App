package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.port.in.RewardUseCase;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import ch.swissqcommerce.backend.exception.ResourceNotFoundException;
import ch.swissqcommerce.backend.exception.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RewardServiceImpl implements RewardUseCase {

    private final RewardOutPort rewardOutPort;
    private final RewardFactory rewardFactory;

    public RewardServiceImpl(RewardOutPort rewardOutPort, RewardFactory rewardFactory) {
        this.rewardOutPort = rewardOutPort;
        this.rewardFactory = rewardFactory;
    }

    @Override
    public void addPoints(String customerId, int amount) {
        if (amount <= 0) {
            throw new RuleViolationException("Amount must be positive");
        }
        rewardFactory.getProcessor(ch.swissqcommerce.backend.domain.reward.core.model.RewardType.POINTS)
                .process(customerId, amount, "Points added via UseCase");
    }

    @Override
    public void redeemPoints(String customerId, int amount) {
        if (amount <= 0) {
            throw new RuleViolationException("Amount must be positive");
        }
        RewardPoints points = rewardOutPort.findRewardPointsByCustomerId(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer points not found"));
        
        if (points.getLoyaltyPoints() < amount) {
            throw new RuleViolationException("Insufficient points");
        }
        
        points.setLoyaltyPoints(points.getLoyaltyPoints() - amount);
        rewardOutPort.saveRewardPoints(points);
    }
}
