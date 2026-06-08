package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransitIncidentRepository extends JpaRepository<TransitIncidentEntity, Integer> {
    List<TransitIncidentEntity> findByRiderRiderId(String riderId);
    List<TransitIncidentEntity> findByOrderOrderId(Integer orderId);
}
