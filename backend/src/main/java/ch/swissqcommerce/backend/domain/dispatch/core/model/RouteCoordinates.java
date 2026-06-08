package ch.swissqcommerce.backend.domain.dispatch.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import lombok.Value;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class RouteCoordinates {
    private final GeoPoint pickup;
    private final GeoPoint dropoff;
    private final List<GeoPoint> path;

    public RouteCoordinates(GeoPoint pickup, GeoPoint dropoff, List<GeoPoint> path) {
        if (pickup == null || dropoff == null) {
            throw new IllegalArgumentException("Pickup and dropoff must be defined");
        }
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.path = path != null ? new ArrayList<>(path) : new ArrayList<>();
    }
}
