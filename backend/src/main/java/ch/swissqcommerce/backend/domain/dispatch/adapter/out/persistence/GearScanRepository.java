package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.GearScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GearScanRepository extends JpaRepository<GearScanEntity, String> {
    List<GearScan> findByRiderIdOrderByScanTimeDesc(String riderId);
}
