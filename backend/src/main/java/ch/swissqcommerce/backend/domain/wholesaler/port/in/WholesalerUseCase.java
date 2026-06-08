package ch.swissqcommerce.backend.domain.wholesaler.port.in;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import java.util.List;
import java.util.Map;

public interface WholesalerUseCase {
    List<B2BRestockOrder> getAssignedRestocks(String wholesalerId);
    B2BRestockOrder createRestockOrder(String storeId, String preferredWholesalerId, String idempotencyKey);
    Map<String, Object> fulfillRestock(Integer restockOrderId);
    Map<String, Object> getInvoiceSummary(String wholesalerId);

    // B2B Replenishment (Purchase Orders & Wastage)
    ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder generateReplenishmentOrders(String storeId, String vendorName, Map<String, Integer> requestedItems);
    ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder receiveGoods(String poId, Map<String, Integer> itemReceipts, String grnFileUrl);
    ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog logWastage(String storeId, String productId, String batchId, Integer qty, String reason, String loggedBy);
}
