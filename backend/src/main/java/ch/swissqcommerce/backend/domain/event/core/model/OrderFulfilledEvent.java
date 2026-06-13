package ch.swissqcommerce.backend.domain.event.core.model;

public class OrderFulfilledEvent extends BaseDomainEvent {

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

    @Override
    public String getEventType() {
        return "OrderFulfilled";
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    @Override
    public String getPayload() {
        return customerId;
    }
}
