package ch.swissqcommerce.backend.domain.catalog.core.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListing {
    private String productId;
    private String title;
    private String description;
    private List<Image> gallery;
    private BigDecimal basePrice;
    private String status; // ACTIVE, ARCHIVED

    public void updatePrice(BigDecimal newPrice) {
        this.basePrice = newPrice;
    }

    public void archive() {
        this.status = "ARCHIVED";
    }
}
