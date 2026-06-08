package ch.swissqcommerce.backend.domain.fleet.core.service;

import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;
import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.port.in.FleetUseCase;
import ch.swissqcommerce.backend.domain.fleet.port.out.FleetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FleetServiceImpl implements FleetUseCase {
    private final FleetPort port;

    @Override
    public RiderShift scheduleShift(RiderShift shift) {
        shift.setStatus("SCHEDULED");
        return port.saveShift(shift);
    }

    @Override
    public PayoutLedger processRiderPayout(String riderId) {
        // Find, process, save
        return null;
    }
}
