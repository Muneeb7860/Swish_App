package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.B2BRestockOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface B2BRestockOrderRepository extends JpaRepository<B2BRestockOrder, Integer> {
    List<B2BRestockOrder> findByWholesalerWholesalerIdOrderByCreatedAtDesc(String wholesalerId);
    Optional<B2BRestockOrder> findByIdempotencyKey(String idempotencyKey);
}
