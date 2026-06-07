package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.adapter.out.persistence.CustomerLoyaltyEntity;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.adapter.out.persistence.RewardPointsEntity;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.List;

@Component
public class RewardPersistenceAdapter implements RewardOutPort {

    private final RewardPointsRepository repository;
    private final CustomerLoyaltyRepository loyaltyRepository;
    private final ch.swissqcommerce.backend.repository.CustomerRepository customerRepository;

    public RewardPersistenceAdapter(RewardPointsRepository repository,
                                    CustomerLoyaltyRepository loyaltyRepository,
                                    ch.swissqcommerce.backend.repository.CustomerRepository customerRepository) {
        this.repository = repository;
        this.loyaltyRepository = loyaltyRepository;
        this.customerRepository = customerRepository;
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

    @Override
    public Optional<ch.swissqcommerce.backend.model.Customer> findCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public ch.swissqcommerce.backend.model.Customer saveCustomer(ch.swissqcommerce.backend.model.Customer customer) {
        return customerRepository.save(customer);
    }
}
