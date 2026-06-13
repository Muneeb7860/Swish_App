package ch.swissqcommerce.backend.domain.reward.port.out;

import ch.swissqcommerce.backend.domain.reward.core.model.CustomerLoyalty;
import ch.swissqcommerce.backend.domain.reward.core.model.RewardPoints;
import ch.swissqcommerce.backend.model.Customer;
import java.util.List;
import java.util.Optional;

public interface RewardOutPort {
    Optional<RewardPoints> findRewardPointsByCustomerId(String customerId);

    void saveRewardPoints(RewardPoints rewardPoints);

    void saveLoyaltyRecord(CustomerLoyalty loyalty);

    List<CustomerLoyalty> findLoyaltyRecordsByCustomerId(String customerId);

    Optional<Customer> findCustomerById(String id);

    Customer saveCustomer(Customer customer);
}
