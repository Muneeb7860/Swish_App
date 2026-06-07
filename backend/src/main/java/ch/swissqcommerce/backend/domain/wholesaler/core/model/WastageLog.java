package ch.swissqcommerce.backend.domain.wholesaler.core.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "wastage_logs", schema = "wholesaler")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WastageLog {
    @Id
    @Column(name = "log_id")
    private String logId;

    @Column(name = "store_id", nullable = false)
    private String storeId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "qty_wasted", nullable = false)
    private Integer qtyWasted;

    @Column(nullable = false)
    private String reason; // EXPIRED, DAMAGED_IN_STORE, MELTED_COLD_CHAIN

    @Column(name = "logged_by", nullable = false)
    private String loggedBy;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime timestamp;
}
