package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "wastage_logs", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WastageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wastage_id")
    private Integer wastageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    @NotNull
    private DarkStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    @NotNull
    private Inventory item;

    @Column(name = "qty_wasted", nullable = false)
    @NotNull
    @Min(1)
    private Integer qtyWasted;

    @Column(name = "reason", length = 30, nullable = false)
    @NotBlank
    @Size(max = 30)
    private String reason; // EXPIRED, DAMAGED, TRANSIT_LOSS, CUSTOMER_REJECTED

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
