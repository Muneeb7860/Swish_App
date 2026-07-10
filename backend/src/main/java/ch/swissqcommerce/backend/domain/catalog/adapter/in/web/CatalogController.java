package ch.swissqcommerce.backend.domain.catalog.adapter.in.web;

import ch.swissqcommerce.backend.domain.catalog.core.model.ProductListing;
import ch.swissqcommerce.backend.domain.catalog.port.in.CatalogUseCase;
import ch.swissqcommerce.backend.domain.catalog.port.in.FmcgCatalogUseCase;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogUseCase catalogUseCase;
    private final FmcgCatalogUseCase fmcgCatalogUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<ProductListing> getProduct(@PathVariable String id) {
        return catalogUseCase
                .getListing(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Catalog mutations are administrator-only. The {@code @PreAuthorize} guard is
     * defense-in-depth: it enforces the role at the method layer even if the gateway/OPA URL-level
     * policy is misconfigured or disabled (it currently fails closed for {@code /api/v1/**}, but
     * this must not be the only control).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody ProductListing listing) {
        if (listing.getTitle() == null || listing.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product title is required."));
        }
        if (listing.getBasePrice() == null
                || listing.getBasePrice().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "basePrice must be present and non-negative."));
        }
        return ResponseEntity.ok(catalogUseCase.createListing(listing));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import-fmcg")
    public ResponseEntity<?> importFmcg(
            @RequestParam(required = false) Boolean isRaining,
            @RequestParam(required = false) Double riderToOrderRatio,
            @RequestParam(required = false) Double vipDensity) {
        return ResponseEntity.ok(
                fmcgCatalogUseCase.importFmcgProducts(isRaining, riderToOrderRatio, vipDensity));
    }
}
