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
        PurchaseOrderEntity entity = toEntity(po);
        PurchaseOrderEntity saved = purchaseOrderRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<PurchaseOrder> findPurchaseOrder(String poId) {
        return purchaseOrderRepository.findById(poId).map(this::toDomain);
    }

    @Override
    public WastageLog saveWastageLog(WastageLog log) {
        WastageLogEntity entity = WastageLogEntity.builder()
                .logId(log.getLogId())
                .storeId(log.getStoreId())
                .productId(log.getProductId())
                .batchId(log.getBatchId())
                .qtyWasted(log.getQtyWasted())
                .reason(log.getReason())
                .loggedBy(log.getLoggedBy())
                .timestamp(log.getTimestamp())
                .build();
        WastageLogEntity saved = wastageLogRepository.save(entity);
        return WastageLog.builder()
                .logId(saved.getLogId())
                .storeId(saved.getStoreId())
                .productId(saved.getProductId())
                .batchId(saved.getBatchId())
                .qtyWasted(saved.getQtyWasted())
                .reason(saved.getReason())
                .loggedBy(saved.getLoggedBy())
                .timestamp(saved.getTimestamp())
                .build();
    }

    private PurchaseOrder toDomain(PurchaseOrderEntity entity) {
        PurchaseOrder domain = PurchaseOrder.builder()
                .poId(entity.getPoId())
                .storeId(entity.getStoreId())
                .vendorName(entity.getVendorName())
                .status(entity.getStatus())
                .inboundDate(entity.getInboundDate())
                .grnVerificationFileUrl(entity.getGrnVerificationFileUrl())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
                
        if (entity.getItems() != null) {
            domain.setItems(entity.getItems().stream().map(itemEntity -> ch.swissqcommerce.backend.domain.wholesaler.core.model.PurchaseOrderItem.builder()
                    .itemId(itemEntity.getItemId())
                    .productId(itemEntity.getProductId())
                    .requestedQty(itemEntity.getRequestedQty())
                    .receivedQty(itemEntity.getReceivedQty())
                    .purchaseOrder(domain)
                    .build()).toList());
        }
        return domain;
    }

    private PurchaseOrderEntity toEntity(PurchaseOrder domain) {
        PurchaseOrderEntity entity = PurchaseOrderEntity.builder()
                .poId(domain.getPoId())
                .storeId(domain.getStoreId())
                .vendorName(domain.getVendorName())
                .status(domain.getStatus())
                .inboundDate(domain.getInboundDate())
                .grnVerificationFileUrl(domain.getGrnVerificationFileUrl())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
                
        if (domain.getItems() != null) {
            entity.setItems(domain.getItems().stream().map(itemDomain -> ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence.PurchaseOrderItemEntity.builder()
                    .itemId(itemDomain.getItemId())
                    .productId(itemDomain.getProductId())
                    .requestedQty(itemDomain.getRequestedQty())
                    .receivedQty(itemDomain.getReceivedQty())
                    .purchaseOrder(entity)
                    .build()).toList());
        }
        return entity;
    }
}
