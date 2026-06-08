package ch.swissqcommerce.backend.domain.fleet.adapter.in.web;

import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.port.in.FleetUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
public class FleetController {
    private final FleetUseCase fleetUseCase;

    @PostMapping("/shifts")
    public ResponseEntity<RiderShift> scheduleShift(@RequestBody RiderShift shift) {
        return ResponseEntity.ok(fleetUseCase.scheduleShift(shift));
    }
}
