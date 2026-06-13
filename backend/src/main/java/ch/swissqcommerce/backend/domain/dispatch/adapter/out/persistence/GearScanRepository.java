package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GearScanRepository extends JpaRepository<GearScanEntity, String> {
    List<GearScanEntity> findByRiderIdOrderByScanTimeDesc(String riderId);
}
