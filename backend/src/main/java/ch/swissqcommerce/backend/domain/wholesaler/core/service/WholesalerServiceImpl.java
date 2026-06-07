package ch.swissqcommerce.backend.domain.wholesaler.core.service;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog;
import ch.swissqcommerce.backend.domain.wholesaler.port.in.WholesalerUseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WholesalerServiceImpl implements WholesalerUseCase {

    @Override
    public List<B2BRestockOrder> getAssignedRestocks(String wholesalerId) {
        return List.of();
    }

    @Override
    public B2BRestockOrder createRestockOrder(String storeId, String preferredWholesalerId, String idempotencyKey) {
        return null;
    }

    @Override
    public Map<String, Object> fulfillRestock(Integer restockOrderId) {
        return Map.of();
    }

    @Override
    public Map<String, Object> getInvoiceSummary(String wholesalerId) {
        return Map.of();
    }

    @Override
    public PurchaseOrder generateReplenishmentOrders(String storeId, String vendorName, Map<String, Integer> requestedItems) {
        return null;
    }

    @Override
    public PurchaseOrder receiveGoods(String poId, Map<String, Integer> itemReceipts, String grnFileUrl) {
        return null;
    }

    @Override
    public WastageLog logWastage(String storeId, String productId, String batchId, Integer qty, String reason, String loggedBy) {
        return null;
    }
}
