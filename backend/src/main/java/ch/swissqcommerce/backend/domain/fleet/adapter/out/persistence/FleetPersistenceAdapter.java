package ch.swissqcommerce.backend.domain.fleet.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.fleet.core.model.PayoutLedger;
import ch.swissqcommerce.backend.domain.fleet.core.model.RiderShift;
import ch.swissqcommerce.backend.domain.fleet.port.out.FleetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FleetPersistenceAdapter implements FleetPort {
    private final RiderShiftRepository shiftRepository;

    @Override
    public RiderShift saveShift(RiderShift shift) {
        RiderShiftEntity entity = RiderShiftEntity.builder()
                .shiftId(shift.getShiftId())
                .riderId(shift.getRiderId())
                .startTime(shift.getStartTime())
                .endTime(shift.getEndTime())
                .status(shift.getStatus())
                .build();
        shiftRepository.save(entity);
        return shift;
    }

    @Override
    public PayoutLedger saveLedger(PayoutLedger ledger) {
        return ledger; // mock
    }
}
