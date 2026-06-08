package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WholesalerRepository extends JpaRepository<WholesalerEntity, String> {
    Optional<WholesalerEntity> findByIsPrimary(Boolean isPrimary);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM oltp.wholesalers WHERE is_active = true AND trust_score >= :minTrust AND wholesaler_id != :excludeId LIMIT 1", nativeQuery = true)
    Optional<WholesalerEntity> findFallbackWholesaler(@org.springframework.data.repository.query.Param("excludeId") String excludeId, @org.springframework.data.repository.query.Param("minTrust") int minTrust);
}
