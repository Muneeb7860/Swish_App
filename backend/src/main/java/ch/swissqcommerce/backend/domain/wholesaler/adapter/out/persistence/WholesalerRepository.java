package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WholesalerRepository extends JpaRepository<WholesalerEntity, String> {
    Optional<WholesalerEntity> findByIsPrimary(Boolean isPrimary);
}
