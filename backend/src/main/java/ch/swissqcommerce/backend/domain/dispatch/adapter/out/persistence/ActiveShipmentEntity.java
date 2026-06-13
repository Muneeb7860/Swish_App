package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "active_shipments", schema = "dispatch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ActiveShipmentEntity {

    @Id
    @Column(name = "shipment_id", length = 50)
    private String shipmentId;

    @Column(name = "order_id", unique = true, nullable = false)
    private Integer orderId;

    @Column(name = "rider_id", length = 50)
    private String riderId;

    @Column(name = "status", length = 25, nullable = false)
    private String status;

    @Column(name = "total_weight_kg", precision = 6, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalWeightKg = BigDecimal.ONE;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "last_gps_update")
    private OffsetDateTime lastGpsUpdate;

    @Column(name = "last_lat", precision = 9, scale = 6)
    private BigDecimal lastLat;

    @Column(name = "last_lng", precision = 9, scale = 6)
    private BigDecimal lastLng;

    @Column(name = "stationary_since")
    private OffsetDateTime stationarySince;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
