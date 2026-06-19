package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import ch.swissqcommerce.backend.model.DarkStore;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "shipments", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shipment_id")
    private Long shipmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private DarkStore warehouse;

    @Column(name = "carrier", length = 50)
    @Size(max = 50)
    private String carrier;

    @Column(name = "estimated_shipping_cost", precision = 10, scale = 2)
    private BigDecimal estimatedShippingCost;

    @Column(name = "actual_shipping_cost", precision = 10, scale = 2)
    private BigDecimal actualShippingCost;

    @Column(name = "status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
