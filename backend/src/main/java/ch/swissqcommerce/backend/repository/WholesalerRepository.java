package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.model.Wholesaler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WholesalerRepository extends JpaRepository<Wholesaler, String> {
    Optional<Wholesaler> findByIsPrimary(Boolean isPrimary);
}
