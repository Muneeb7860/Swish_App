package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WholesalerRepository extends JpaRepository<WholesalerEntity, String> {
    Optional<WholesalerEntity> findByIsPrimary(Boolean isPrimary);
}
