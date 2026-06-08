package ch.swissqcommerce.backend.domain.wholesaler.port.out;

import ch.swissqcommerce.backend.domain.wholesaler.core.model.Wholesaler;
import java.util.List;
import java.util.Optional;

public interface WholesalerPort {
    Optional<Wholesaler> findById(String wholesalerId);
    Optional<Wholesaler> findByIsPrimary(Boolean isPrimary);
    List<Wholesaler> findAll();
    Optional<Wholesaler> findFallbackWholesaler(String excludeId, int minTrust);
    Wholesaler save(Wholesaler wholesaler);
}
