package ch.swissqcommerce.backend.domain.transaction.port.in;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public interface OrderUseCase {
    List<Order> getCustomerOrders(String customerId);

    Order getOrderById(Integer orderId);

    record CartItem(@JsonProperty("item_id") String itemId, int quantity) {}

    Order checkout(
            String customerId,
            List<CartItem> items,
            String paymentMethod,
            BigDecimal tip,
            Integer bagsReturned,
            String idempotencyKey);

    java.util.Map<String, Object> requestRefund(
            Integer orderId,
            String claimReason,
            BigDecimal customerLatitude,
            BigDecimal customerLongitude);
}
