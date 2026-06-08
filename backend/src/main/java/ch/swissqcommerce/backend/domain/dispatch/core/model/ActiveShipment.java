package ch.swissqcommerce.backend.domain.dispatch.core.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveShipment {
    private String shipmentId;
    private Integer orderId;
    private String riderId;
    private ShipmentStatus status;
    private BigDecimal totalWeightKg;
    private OffsetDateTime assignedAt;
    private OffsetDateTime lastGpsUpdate;
    private BigDecimal lastLat;
    private BigDecimal lastLng;
    private OffsetDateTime stationarySince;
    private OffsetDateTime updatedAt;
}
