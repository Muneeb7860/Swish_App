package ch.swissqcommerce.backend.domain.wholesaler.port.in;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import java.util.List;
import java.util.Map;

public interface WholesalerUseCase {
    List<B2BRestockOrder> getAssignedRestocks(String wholesalerId);
    B2BRestockOrder createRestockOrder(String storeId, String preferredWholesalerId, String idempotencyKey);
    Map<String, Object> fulfillRestock(Integer restockOrderId);
    Map<String, Object> getInvoiceSummary(String wholesalerId);
}
