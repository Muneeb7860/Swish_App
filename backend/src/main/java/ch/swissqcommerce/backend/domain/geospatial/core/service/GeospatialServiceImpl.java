package ch.swissqcommerce.backend.domain.geospatial.core.service;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import ch.swissqcommerce.backend.domain.geospatial.port.in.GeospatialUseCase;
import ch.swissqcommerce.backend.domain.geospatial.port.out.GeospatialPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeospatialServiceImpl implements GeospatialUseCase {
    private final GeospatialPort port;

    @Override
    public DeliveryZone createZone(DeliveryZone zone) {
        zone.activate();
        return port.save(zone);
    }

    @Override
    public Optional<DeliveryZone> getZone(String zoneId) {
        return port.findById(zoneId);
    }
}
