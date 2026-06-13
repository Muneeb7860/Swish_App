package ch.swissqcommerce.backend.domain.fleet.port.in;

import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;
import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;

public interface FleetUseCase {
    RiderShift scheduleShift(RiderShift shift);

    PayoutLedger processRiderPayout(String riderId);
}
