package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "warehouse_baseline", schema = "oltp")
@IdClass(WarehouseBaselineId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseBaseline {

    @Id
    @Column(name = "zip_prefix", length = 5, nullable = false)
    private String zipPrefix;

    @Id
    @Column(name = "warehouse_id", length = 50, nullable = false)
    private String warehouseId;

    @Column(name = "avg_shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgShippingCost;

    @Column(name = "sample_size", nullable = false)
    @Builder.Default
    private Integer sampleSize = 0;

    @Column(name = "last_updated", insertable = false, updatable = false)
    private OffsetDateTime lastUpdated;
}
