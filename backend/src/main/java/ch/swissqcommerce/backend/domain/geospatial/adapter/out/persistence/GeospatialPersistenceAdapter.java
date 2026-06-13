package ch.swissqcommerce.backend.domain.geospatial.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import ch.swissqcommerce.backend.domain.geospatial.port.out.GeospatialPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeospatialPersistenceAdapter implements GeospatialPort {
    private final DeliveryZoneRepository repository;

    @Override
    public DeliveryZone save(DeliveryZone zone) {
        DeliveryZoneEntity entity =
                DeliveryZoneEntity.builder()
                        .zoneId(zone.getZoneId())
                        .name(zone.getName())
                        .geoPolygonWkt(zone.getGeoPolygonWkt())
                        .status(zone.getStatus())
                        .build();
        repository.save(entity);
        return zone;
    }

    @Override
    public Optional<DeliveryZone> findById(String zoneId) {
        return repository
                .findById(zoneId)
                .map(
                        e ->
                                DeliveryZone.builder()
                                        .zoneId(e.getZoneId())
                                        .name(e.getName())
                                        .geoPolygonWkt(e.getGeoPolygonWkt())
                                        .status(e.getStatus())
                                        .build());
    }
}
