package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class CashbackRewardProcessor implements RewardProcessor {

    private final CustomerRepository customerRepository;

    public CashbackRewardProcessor(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public RewardType getType() {
        return RewardType.CASHBACK;
    }

    @Override
    public void process(String customerId, int amount, String description) {
        customerRepository.findById(customerId).ifPresent(customer -> {
            BigDecimal cashbackAmount = BigDecimal.valueOf(amount);
            customer.setWalletBalance(customer.getWalletBalance().add(cashbackAmount));
            customerRepository.save(customer);
        });
    }
}
