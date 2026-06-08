package ch.swissqcommerce.backend.domain.ordermanagement.port.in;

public interface OrderManagementUseCase {
    void handleOrderCreated(String orderId, String customerId);
    void handleInventoryConfirmed(String orderId);
    void handlePaymentSuccess(String orderId);
    void handleOrderDelivered(String orderId);
    void compensateOrder(String orderId, String reason);
}
