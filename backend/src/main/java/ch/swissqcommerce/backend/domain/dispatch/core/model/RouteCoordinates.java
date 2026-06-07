package ch.swissqcommerce.backend.domain.dispatch.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import lombok.Value;
import java.util.ArrayList;
import java.util.List;

@Value
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteCoordinates {
    GeoPoint pickup;
    GeoPoint dropoff;
    List<GeoPoint> path;

    public RouteCoordinates(GeoPoint pickup, GeoPoint dropoff, List<GeoPoint> path) {
        if (pickup == null || dropoff == null) {
            throw new IllegalArgumentException("Pickup and dropoff must be defined");
        }
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.path = path != null ? new ArrayList<>(path) : new ArrayList<>();
    }
}
