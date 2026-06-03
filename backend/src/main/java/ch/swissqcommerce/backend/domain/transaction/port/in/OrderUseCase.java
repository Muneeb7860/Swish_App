package ch.swissqcommerce.backend.domain.transaction.port.in;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;

import java.math.BigDecimal;
import java.util.List;

public interface OrderUseCase {
    List<Order> getCustomerOrders(String customerId);

    record CartItem(String itemId, int quantity) {}

    Order checkout(String customerId, List<CartItem> items, String paymentMethod,
                   BigDecimal tip, Integer bagsReturned, String idempotencyKey);

    java.util.Map<String, Object> requestRefund(Integer orderId, String claimReason, BigDecimal customerLatitude, BigDecimal customerLongitude);
}
