package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcurementApprovalEntityRepository extends JpaRepository<ProcurementApprovalEntity, Integer> {
}
