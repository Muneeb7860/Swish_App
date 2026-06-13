package ch.swissqcommerce.backend.domain.governance.port.out;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import java.util.List;
import java.util.Optional;

public interface ProcurementApprovalPort {
    Optional<ProcurementApproval> findById(Integer id);

    ProcurementApproval save(ProcurementApproval approval);

    List<ProcurementApproval> findAll();
}
