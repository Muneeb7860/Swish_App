package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, String> {
}
