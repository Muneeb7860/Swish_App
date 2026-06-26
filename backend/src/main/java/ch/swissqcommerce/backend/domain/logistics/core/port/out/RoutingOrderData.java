package ch.swissqcommerce.backend.domain.logistics.core.port.out;

import ch.swissqcommerce.backend.model.CustomerAddress;
import ch.swissqcommerce.backend.model.DarkStore;
import java.util.List;

/**
 * Port-level representation of order data needed by logistics core services. Avoids coupling
 * logistics core to transaction adapter persistence entities.
 */
public record RoutingOrderData(
        Integer orderId, CustomerAddress customerAddress, DarkStore store, List<OrderItem> items) {
    /** Minimal order item data needed for warehouse selection. */
    public record OrderItem(String itemId, int quantity) {}
}
