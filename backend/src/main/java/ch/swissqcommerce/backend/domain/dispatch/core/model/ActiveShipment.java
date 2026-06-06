package ch.swissqcommerce.backend.domain.dispatch.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "active_shipments", schema = "dispatch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveShipment {

    @Id
    @Column(name = "shipment_id", length = 50)
    @NotBlank
    @Size(max = 50)
    private String shipmentId;

    @Column(name = "order_id", unique = true, nullable = false)
    @NotNull
    private Integer orderId;

    @Column(name = "rider_id", length = 50)
    @Size(max = 50)
    private String riderId;

    @Column(name = "status", length = 25, nullable = false)
    @NotBlank
    @Size(max = 25)
    private String status;

    @Column(name = "total_weight_kg", precision = 6, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal totalWeightKg;

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
}
