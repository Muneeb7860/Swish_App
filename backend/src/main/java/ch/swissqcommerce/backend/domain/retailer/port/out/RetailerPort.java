package ch.swissqcommerce.backend.domain.retailer.port.out;

import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;

import java.util.Optional;

public interface RetailerPort {
    Retailer save(Retailer retailer);
    Optional<Retailer> findById(String retailerId);
    Optional<Retailer> findByApiKeyHash(String apiKeyHash);
}
