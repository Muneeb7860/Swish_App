package ch.swissqcommerce.backend.domain.reward.core.service;

import ch.swissqcommerce.backend.domain.reward.core.model.RewardType;
import ch.swissqcommerce.backend.domain.reward.port.out.RewardOutPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CashbackRewardProcessor implements RewardProcessor {

    private final RewardOutPort rewardOutPort;

    public CashbackRewardProcessor(RewardOutPort rewardOutPort) {
        this.rewardOutPort = rewardOutPort;
    }

    @Override
    public RewardType getType() {
        return RewardType.CASHBACK;
    }

    @Override
    public void process(String customerId, int amount, String description) {
        rewardOutPort
                .findCustomerById(customerId)
                .ifPresent(
                        customer -> {
                            BigDecimal cashbackAmount = BigDecimal.valueOf(amount);
                            customer.setWalletBalance(
                                    customer.getWalletBalance().add(cashbackAmount));
                            rewardOutPort.saveCustomer(customer);
                        });
    }
}
