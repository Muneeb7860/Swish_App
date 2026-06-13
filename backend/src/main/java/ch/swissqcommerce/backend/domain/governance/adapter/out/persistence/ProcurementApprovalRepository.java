package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcurementApprovalRepository
        extends JpaRepository<ProcurementApprovalEntity, Integer> {}
