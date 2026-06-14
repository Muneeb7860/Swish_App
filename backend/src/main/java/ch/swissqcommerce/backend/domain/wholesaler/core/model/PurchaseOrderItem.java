package ch.swissqcommerce.backend.domain.wholesaler.core.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderItem {

    private String itemId;

    private PurchaseOrder purchaseOrder;

    private String productId;

    private Integer requestedQty;

    @Builder.Default private Integer receivedQty = 0;
}
