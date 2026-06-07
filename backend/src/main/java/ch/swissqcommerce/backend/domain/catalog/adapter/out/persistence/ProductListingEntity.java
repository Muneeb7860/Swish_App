package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "product_listings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListingEntity {
    @Id
    private String productId;
    private String title;
    private String description;
    private BigDecimal basePrice;
    private String status;
}
