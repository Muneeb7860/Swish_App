package ch.swissqcommerce.backend.domain.catalog.core.service;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl implements CatalogUseCase {
    private final CatalogPort port;

    @Override
    @CachePut(value = "catalog", key = "#result.productId")
    public ProductListing createListing(ProductListing listing) {
        if (listing.getProductId() == null) listing.setProductId(UUID.randomUUID().toString());
        return port.save(listing);
    }

    @Override
    @Cacheable(value = "catalog", key = "#productId")
    public Optional<ProductListing> getListing(String productId) {
        return port.findById(productId);
    }
}
