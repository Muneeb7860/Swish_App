package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.List;

@Component
public class RewardPersistenceAdapter implements RewardOutPort {

    private final RewardPointsRepository repository;
    private final CustomerLoyaltyRepository loyaltyRepository;

    public RewardPersistenceAdapter(RewardPointsRepository repository,
                                    CustomerLoyaltyRepository loyaltyRepository) {
        this.repository = repository;
        this.loyaltyRepository = loyaltyRepository;
    }

    @Override
    public Optional<RewardPoints> findRewardPointsByCustomerId(String customerId) {
        return repository.findById(customerId);
    }

    @Override
    public void saveRewardPoints(RewardPoints rewardPoints) {
        repository.save(rewardPoints);
    }

    @Override
    public void saveLoyaltyRecord(CustomerLoyalty loyalty) {
        loyaltyRepository.save(loyalty);
    }

    @Override
    public List<CustomerLoyalty> findLoyaltyRecordsByCustomerId(String customerId) {
        return loyaltyRepository.findByCustomerId(customerId);
    }
}
