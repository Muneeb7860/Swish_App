package ch.swissqcommerce.backend.domain.catalog.port.in;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import java.util.Optional;

public interface CatalogUseCase {
    ProductListing createListing(ProductListing listing);

    Optional<ProductListing> getListing(String productId);
}
