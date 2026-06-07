package ch.swissqcommerce.backend.domain.catalog.port.out;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import java.util.Optional;

public interface CatalogPort {
    ProductListing save(ProductListing listing);
    Optional<ProductListing> findById(String productId);
}
