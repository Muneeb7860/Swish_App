package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.out.CatalogPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogPersistenceAdapter implements CatalogPort {
    private final CatalogRepository repository;

    @Override
    public ProductListing save(ProductListing listing) {
        ProductListingEntity entity =
                ProductListingEntity.builder()
                        .productId(listing.getProductId())
                        .title(listing.getTitle())
                        .description(listing.getDescription())
                        .basePrice(listing.getBasePrice())
                        .status(listing.getStatus())
                        .build();
        repository.save(entity);
        return listing;
    }

    @Override
    public Optional<ProductListing> findById(String productId) {
        return repository
                .findById(productId)
                .map(
                        e ->
                                ProductListing.builder()
                                        .productId(e.getProductId())
                                        .title(e.getTitle())
                                        .description(e.getDescription())
                                        .basePrice(e.getBasePrice())
                                        .status(e.getStatus())
                                        .build());
    }
}
