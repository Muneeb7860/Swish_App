package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.VehicleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleConfigRepository extends JpaRepository<VehicleConfig, String> {
}
