package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.TransitIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransitIncidentRepository extends JpaRepository<TransitIncident, Integer> {
    List<TransitIncident> findByRiderRiderId(String riderId);
    List<TransitIncident> findByOrderOrderId(Integer orderId);
}
