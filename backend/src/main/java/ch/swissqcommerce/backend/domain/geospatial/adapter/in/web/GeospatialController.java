package ch.swissqcommerce.backend.domain.geospatial.adapter.in.web;

import ch.swissqcommerce.backend.domain.geospatial.core.model.DeliveryZone;
import ch.swissqcommerce.backend.domain.geospatial.port.in.GeospatialUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/geospatial")
@RequiredArgsConstructor
public class GeospatialController {
    private final GeospatialUseCase geoUseCase;

    @GetMapping("/zones/{id}")
    public ResponseEntity<DeliveryZone> getZone(@PathVariable String id) {
        return geoUseCase.getZone(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/zones")
    public ResponseEntity<DeliveryZone> createZone(@RequestBody DeliveryZone zone) {
        return ResponseEntity.ok(geoUseCase.createZone(zone));
    }
}
