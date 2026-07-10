package ch.swissqcommerce.backend.domain.catalog.port.in;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface FmcgCatalogUseCase {
    List<FmcgImportResult> importFmcgProducts();

    List<FmcgImportResult> importFmcgProducts(
            Boolean isRaining, Double riderToOrderRatio, Double vipDensity);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class FmcgImportResult {
        private String barcode;
        private String name;
        private String brand;
        private String category;
        private String emoji;
        private BigDecimal basePrice;
        private BigDecimal dynamicPrice;
        private double surgeMultiplier;
        private double discountPercent;
        private String pricingRationale;
        private String source; // "API" or "FALLBACK"
        private String status; // "SUCCESS" or "FAILED"
    }
}
