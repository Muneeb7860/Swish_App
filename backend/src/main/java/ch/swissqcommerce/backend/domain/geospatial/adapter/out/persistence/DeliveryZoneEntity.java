package ch.swissqcommerce.backend.domain.geospatial.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delivery_zones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryZoneEntity {
    @Id
    private String zoneId;
    private String name;
    private String geoPolygonWkt;
    private String status;
}
