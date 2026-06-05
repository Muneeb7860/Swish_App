package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcurementApprovalRepository extends JpaRepository<ProcurementApproval, Integer> {
}
