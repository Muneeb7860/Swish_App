package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveShipmentRepository extends JpaRepository<ActiveShipmentEntity, String> {
    Optional<ActiveShipment> findByOrderId(Integer orderId);
    List<ActiveShipment> findByRiderIdAndStatus(String riderId, String status);
    List<ActiveShipment> findByStatusIn(List<String> statuses);
}
