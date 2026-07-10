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
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogUseCase catalogUseCase;
    private final FmcgCatalogUseCase fmcgCatalogUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<ProductListing> getProduct(@PathVariable String id) {
        return catalogUseCase.getProduct(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/calculate-dynamic-price")
    public ResponseEntity<BigDecimal> calculateDynamicPrice(@RequestBody Map<String, Object> request) {
        String basePriceStr = (String) request.get("basePrice");
        Boolean isRaining = (Boolean) request.get("isRaining");
        Double riderToOrderRatio = (Double) request.get("riderToOrderRatio");
        Double competitorPrice = (Double) request.get("competitorPrice");
        Integer daysToExpiry = (Integer) request.get("daysToExpiry");
        Double vipDensity = (Double) request.get("vipDensity");

        BigDecimal basePrice = new BigDecimal(basePriceStr);
        return ResponseEntity.ok(catalogUseCase.calculateDynamicPrice(basePrice, isRaining, riderToOrderRatio, competitorPrice, daysToExpiry, vipDensity));
    }

    @PostMapping
    public ResponseEntity<ProductListing> createListing(@RequestBody ProductListing listing) {
        if (listing.getProductId() == null) {
            return ResponseEntity.badRequest().build();
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
