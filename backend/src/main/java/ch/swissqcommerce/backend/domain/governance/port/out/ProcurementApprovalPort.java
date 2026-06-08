package ch.swissqcommerce.backend.domain.governance.port.out;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import java.util.Optional;
import java.util.List;

public interface ProcurementApprovalPort {
    Optional<ProcurementApproval> findById(Integer id);
    ProcurementApproval save(ProcurementApproval approval);
    List<ProcurementApproval> findAll();
}
