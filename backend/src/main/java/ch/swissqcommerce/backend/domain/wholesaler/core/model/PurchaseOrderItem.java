package ch.swissqcommerce.backend.domain.wholesaler.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_order_items", schema = "wholesaler")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItem {
    @Id
    @Column(name = "item_id")
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "received_qty", nullable = false)
    @Builder.Default
    private Integer receivedQty = 0;
}
