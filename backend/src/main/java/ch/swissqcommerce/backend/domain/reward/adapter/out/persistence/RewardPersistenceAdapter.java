package ch.swissqcommerce.backend.domain.reward.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RewardPersistenceAdapter implements RewardOutPort {

    private final RewardPointsRepository repository;
    private final CustomerLoyaltyRepository loyaltyRepository;
    private final ch.swissqcommerce.backend.repository.CustomerRepository customerRepository;

    public RewardPersistenceAdapter(
            RewardPointsRepository repository,
            CustomerLoyaltyRepository loyaltyRepository,
            ch.swissqcommerce.backend.repository.CustomerRepository customerRepository) {
        this.repository = repository;
        this.loyaltyRepository = loyaltyRepository;
        this.customerRepository = customerRepository;
    }

    private RewardPointsEntity toEntity(RewardPoints points) {
        if (points == null) return null;
        return RewardPointsEntity.builder()
                .customerId(points.getCustomerId())
                .loyaltyPoints(points.getLoyaltyPoints())
                .build();
    }

    private RewardPoints toDomain(RewardPointsEntity entity) {
        if (entity == null) return null;
        return RewardPoints.builder()
                .customerId(entity.getCustomerId())
                .loyaltyPoints(entity.getLoyaltyPoints())
                .build();
    }

    private CustomerLoyaltyEntity toEntity(CustomerLoyalty loyalty) {
        if (loyalty == null) return null;
        return CustomerLoyaltyEntity.builder()
                .loyaltyId(loyalty.getLoyaltyId())
                .customerId(loyalty.getCustomerId())
                .pointsChanged(loyalty.getPointsChanged())
                .description(loyalty.getDescription())
                .createdAt(loyalty.getCreatedAt())
                .build();
    }

    private CustomerLoyalty toDomain(CustomerLoyaltyEntity entity) {
        if (entity == null) return null;
        return CustomerLoyalty.builder()
                .loyaltyId(entity.getLoyaltyId())
                .customerId(entity.getCustomerId())
                .pointsChanged(entity.getPointsChanged())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Override
    public Optional<RewardPoints> findRewardPointsByCustomerId(String customerId) {
        return repository.findById(customerId).map(this::toDomain);
    }

    @Override
    public void saveRewardPoints(RewardPoints rewardPoints) {
        repository
                .findById(rewardPoints.getCustomerId())
                .ifPresentOrElse(
                        entity -> {
                            entity.setLoyaltyPoints(rewardPoints.getLoyaltyPoints());
                            repository.save(entity);
                        },
                        () -> repository.save(toEntity(rewardPoints)));
    }

    @Override
    public void saveLoyaltyRecord(CustomerLoyalty loyalty) {
        loyaltyRepository.save(toEntity(loyalty));
    }

    @Override
    public List<CustomerLoyalty> findLoyaltyRecordsByCustomerId(String customerId) {
        return loyaltyRepository.findByCustomerId(customerId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ch.swissqcommerce.backend.model.Customer> findCustomerById(String id) {
        return customerRepository.findById(id);
    }

    @Override
    public ch.swissqcommerce.backend.model.Customer saveCustomer(
            ch.swissqcommerce.backend.model.Customer customer) {
        return customerRepository.save(customer);
    }
}
