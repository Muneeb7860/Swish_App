package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrder;
import ch.swissqcommerce.backend.domain.wholesaler.core.model.WastageLog;
import ch.swissqcommerce.backend.domain.wholesaler.port.out.PurchaseOrderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PurchaseOrderPersistenceAdapter implements PurchaseOrderPort {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final WastageLogRepository wastageLogRepository;

    @Override
    public PurchaseOrder savePurchaseOrder(PurchaseOrder po) {
        return purchaseOrderRepository.save(po);
    }

    @Override
    public Optional<PurchaseOrder> findPurchaseOrder(String poId) {
        return purchaseOrderRepository.findById(poId);
    }

    @Override
    public WastageLog saveWastageLog(WastageLog log) {
        return wastageLogRepository.save(log);
    }
}
