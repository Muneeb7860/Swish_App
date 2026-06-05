package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import org.springframework.stereotype.Component;

@Component
public class BadgeRewardProcessor implements RewardProcessor {

    private final CustomerRepository customerRepository;

    public BadgeRewardProcessor(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public RewardType getType() {
        return RewardType.BADGE;
    }

    @Override
    public void process(String customerId, int amount, String description) {
        customerRepository.findById(customerId).ifPresent(customer -> {
            int newScore = Math.min(100, customer.getTrustScore() + amount);
            customer.setTrustScore(newScore);
            if (customer.getConsecutiveOrdersCompleted() >= 5) {
                customer.setVipStatus(true);
            }
            customerRepository.save(customer);
        });
    }
}
