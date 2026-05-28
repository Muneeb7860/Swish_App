package ch.swissqcommerce.backend.domain.reward.port.in;

public interface RewardUseCase {
    void addPoints(String customerId, int amount);
    void redeemPoints(String customerId, int amount);
}
