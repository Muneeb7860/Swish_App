package ch.swissqcommerce.backend.domain.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_listings", schema = "oltp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListingEntity {

    @Id
    @Column(name = "product_id", length = 50)
    private String productId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal basePrice;

    @Column(name = "status", length = 30, nullable = false)
    private String status;
}
