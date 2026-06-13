package ch.swissqcommerce.backend.domain.geospatial.port.in;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import java.util.Optional;

public interface GeospatialUseCase {
    DeliveryZone createZone(DeliveryZone zone);

    Optional<DeliveryZone> getZone(String zoneId);
}
