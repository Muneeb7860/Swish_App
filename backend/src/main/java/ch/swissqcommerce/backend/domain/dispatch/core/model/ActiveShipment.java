package ch.swissqcommerce.backend.domain.dispatch.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@Builder
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveShipment {
    private final String shipmentId;
    private final String orderId;
    private String riderId;
    private RouteCoordinates route;
    private ShipmentStatus status;

    public void assignRider(String riderId) {
        if (this.status != ShipmentStatus.UNASSIGNED) {
            throw new IllegalStateException("Shipment is already assigned or in progress");
        }
        if (riderId == null || riderId.isBlank()) {
            throw new IllegalArgumentException("Rider ID must be provided");
        }
        this.riderId = riderId;
        this.status = ShipmentStatus.ASSIGNED;
    }

    public void updateLocation(BigDecimal lat, BigDecimal lng) {
        if (this.status == ShipmentStatus.UNASSIGNED || this.status == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Cannot update location for unassigned or delivered shipment");
        }
        
        GeoPoint currentLoc = new GeoPoint(lat, lng);
        this.route.getPath().add(currentLoc);
        
        if (this.status == ShipmentStatus.ASSIGNED) {
            this.status = ShipmentStatus.IN_TRANSIT;
        }
    }

    public void markDelivered() {
        if (this.status != ShipmentStatus.IN_TRANSIT && this.status != ShipmentStatus.ASSIGNED) {
            throw new IllegalStateException("Cannot deliver a shipment that is not in transit");
        }
        this.status = ShipmentStatus.DELIVERED;
    }
}
