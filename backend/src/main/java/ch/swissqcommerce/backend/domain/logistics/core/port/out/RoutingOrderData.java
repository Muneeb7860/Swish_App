package ch.swissqcommerce.backend.domain.logistics.core.port.out;

import ch.swissqcommerce.backend.model.CustomerAddress;
import ch.swissqcommerce.backend.model.DarkStore;
import java.math.BigDecimal;
import java.util.List;

/**
 * Port-level representation of order data needed by logistics core services. Avoids coupling
 * logistics core to transaction adapter persistence entities.
 */
public record RoutingOrderData(
        Integer orderId,
        CustomerAddress customerAddress,
        DarkStore store,
        List<OrderItem> items,
        /** Optional: maximum days the customer is willing to wait for delivery. */
        Integer requestedDeliveryDays) {

    /** Convenience constructor for callers that do not specify a delivery SLA. */
    public RoutingOrderData(
            Integer orderId,
            CustomerAddress customerAddress,
            DarkStore store,
            List<OrderItem> items) {
        this(orderId, customerAddress, store, items, null);
    }

    /** Minimal order item data needed for warehouse selection and multi-package routing. */
    public record OrderItem(
            String itemId,
            int quantity,
            /** Optional: weight of a single unit in kilograms, for carrier weight-limit checks. */
            BigDecimal weightKg,
            /** Optional: whether this item is flagged as fragile, drives carrier selection. */
            boolean fragile) {

        /** Convenience constructor for callers that do not supply weight/fragile data. */
        public OrderItem(String itemId, int quantity) {
            this(itemId, quantity, null, false);
        }
    }
}
