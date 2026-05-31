package ch.swissqcommerce.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @Column(name = "item_id", length = 50)
    @Size(max = 50)
    private String itemId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "store_id")
    private DarkStore store;

    @Column(name = "name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "price", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    @Min(0)
    @Builder.Default
    private Integer stock = 0;

    @Column(name = "category", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String category;

    @Column(name = "emoji", length = 10, nullable = false)
    @NotBlank
    @Size(max = 10)
    private String emoji;

    @Column(name = "perishable", nullable = false)
    @Builder.Default
    private Boolean perishable = false;

    @Version
    private Long version;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
