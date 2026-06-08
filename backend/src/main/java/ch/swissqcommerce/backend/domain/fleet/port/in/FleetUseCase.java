package ch.swissqcommerce.backend.domain.fleet.port.in;

import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;

public interface FleetUseCase {
    RiderShift scheduleShift(RiderShift shift);
    PayoutLedger processRiderPayout(String riderId);
}
