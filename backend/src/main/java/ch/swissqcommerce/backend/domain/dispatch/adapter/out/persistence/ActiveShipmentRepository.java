package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.dispatch.core.model.ActiveShipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveShipmentRepository extends JpaRepository<ActiveShipmentEntity, String> {
    Optional<ActiveShipmentEntity> findByOrderId(Integer orderId);
    List<ActiveShipmentEntity> findByRiderIdAndStatus(String riderId, String status);
    List<ActiveShipmentEntity> findByStatusIn(List<String> statuses);
}
