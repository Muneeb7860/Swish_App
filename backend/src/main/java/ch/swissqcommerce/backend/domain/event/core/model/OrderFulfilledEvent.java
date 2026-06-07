package ch.swissqcommerce.backend.domain.event.core.model;

public class OrderFulfilledEvent extends BaseDomainEventEntity {
    
    private final String customerId;
    private final String orderId;
    private final int rewardPoints;

    public OrderFulfilledEvent(String customerId, String orderId, int rewardPoints) {
        super();
        this.customerId = customerId;
        this.orderId = orderId;
        this.rewardPoints = rewardPoints;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }
}
