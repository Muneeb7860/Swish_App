package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface B2BRestockOrderRepository extends JpaRepository<B2BRestockOrderEntity, Integer> {
    List<B2BRestockOrderEntity> findByWholesalerWholesalerIdOrderByCreatedAtDesc(
            String wholesalerId);

    Optional<B2BRestockOrderEntity> findByIdempotencyKey(String idempotencyKey);
}
