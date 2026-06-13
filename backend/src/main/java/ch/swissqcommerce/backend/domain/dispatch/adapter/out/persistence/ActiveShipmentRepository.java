package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActiveShipmentRepository extends JpaRepository<ActiveShipmentEntity, String> {
    Optional<ActiveShipmentEntity> findByOrderId(Integer orderId);

    List<ActiveShipmentEntity> findByRiderIdAndStatus(String riderId, String status);

    List<ActiveShipmentEntity> findByStatusIn(List<String> statuses);
}
