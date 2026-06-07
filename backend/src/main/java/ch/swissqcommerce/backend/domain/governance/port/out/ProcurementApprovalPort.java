package ch.swissqcommerce.backend.domain.governance.port.out;

import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalEntity;
import java.util.Optional;
import java.util.List;

public interface ProcurementApprovalEntityPort {
    Optional<ProcurementApprovalEntity> findById(Integer id);
    ProcurementApprovalEntity save(ProcurementApprovalEntity approval);
    List<ProcurementApprovalEntity> findAll();
}
