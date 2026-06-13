package ch.swissqcommerce.backend.domain.fleet.port.out;

import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;
import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;

public interface FleetPort {
    RiderShift saveShift(RiderShift shift);

    PayoutLedger saveLedger(PayoutLedger ledger);
}
