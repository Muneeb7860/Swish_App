package ch.swissqcommerce.backend.domain.wholesaler.port.out;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog;
import java.util.Optional;

public interface PurchaseOrderPort {
    PurchaseOrder savePurchaseOrder(PurchaseOrder po);
    Optional<PurchaseOrder> findPurchaseOrder(String poId);
    WastageLog saveWastageLog(WastageLog log);
}
