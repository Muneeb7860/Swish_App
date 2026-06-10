package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface B2BRestockOrderRepository extends JpaRepository<B2BRestockOrderEntity, Integer> {
    List<B2BRestockOrderEntity> findByWholesalerWholesalerIdOrderByCreatedAtDesc(String wholesalerId);
    Optional<B2BRestockOrderEntity> findByIdempotencyKey(String idempotencyKey);
}
