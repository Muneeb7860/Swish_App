package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRepository extends JpaRepository<ProductListingEntity, String> {
}
