package ch.swissqcommerce.backend.domain.reward.core.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoyaltyWallet {
    private final String walletId;
    private final String customerId;
    private Points balance;

    public void awardPoints(int amount) {
        if (this.balance == null) {
            this.balance = new Points(0);
        }
        this.balance = this.balance.add(amount);
    }

    public void redeemPoints(int amount) {
        if (this.balance == null) {
            this.balance = new Points(0);
        }
        this.balance = this.balance.subtract(amount);
    }
}
