package ch.swissqcommerce.backend.domain.geospatial.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryZone {
    private String zoneId;
    private String name;
    private String geoPolygonWkt;
    private String status;

    public void suspend() { this.status = "SUSPENDED"; }
    public void activate() { this.status = "ACTIVE"; }
}
