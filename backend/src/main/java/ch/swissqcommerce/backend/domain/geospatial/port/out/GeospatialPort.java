package ch.swissqcommerce.backend.domain.geospatial.port.out;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import java.util.Optional;

public interface GeospatialPort {
    DeliveryZone save(DeliveryZone zone);

    Optional<DeliveryZone> findById(String zoneId);
}
