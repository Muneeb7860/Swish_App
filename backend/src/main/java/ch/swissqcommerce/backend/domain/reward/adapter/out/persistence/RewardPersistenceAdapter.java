package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class RewardPersistenceAdapter implements RewardOutPort {

    private final RewardPointsRepository repository;

    public RewardPersistenceAdapter(RewardPointsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RewardPoints> findRewardPointsByCustomerId(String customerId) {
        return repository.findById(customerId);
    }

    @Override
    public void saveRewardPoints(RewardPoints rewardPoints) {
        repository.save(rewardPoints);
    }
}
