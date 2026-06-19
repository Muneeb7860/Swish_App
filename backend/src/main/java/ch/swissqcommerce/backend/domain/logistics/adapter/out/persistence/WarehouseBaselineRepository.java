package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseBaselineRepository extends JpaRepository<WarehouseBaseline, WarehouseBaselineId> {
    List<WarehouseBaseline> findByZipPrefix(String zipPrefix);
}
