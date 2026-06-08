package ch.swissqcommerce.backend.domain.catalog.core.service;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogUseCase {
    private final CatalogPort port;

    @Override
    public ProductListing createListing(ProductListing listing) {
        if (listing.getProductId() == null) listing.setProductId(UUID.randomUUID().toString());
        return port.save(listing);
    }

    @Override
    public Optional<ProductListing> getListing(String productId) {
        return port.findById(productId);
    }
}
