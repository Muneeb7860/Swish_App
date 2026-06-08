package ch.swissqcommerce.backend.domain.catalog.adapter.in.web;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogUseCase catalogUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<ProductListing> getProduct(@PathVariable String id) {
        return catalogUseCase.getListing(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductListing> createProduct(@RequestBody ProductListing listing) {
        return ResponseEntity.ok(catalogUseCase.createListing(listing));
    }
}
