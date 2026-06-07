package ch.swissqcommerce.backend.domain.wholesaler.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "wholesalers", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WholesalerEntity {

    @Id
    @Column(name = "wholesaler_id", length = 50)
    @Size(max = 50)
    private String wholesalerId;

    @Column(name = "name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = true;

    @Column(name = "trust_score", nullable = false)
    @Min(0)
    @Max(100)
    @Builder.Default
    private Integer trustScore = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "academy_discount_active", nullable = false)
    @Builder.Default
    private Boolean academyDiscountActive = false;

    @Column(name = "base_invoice_amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal baseInvoiceAmount = new BigDecimal("25.00");

    @Column(name = "fallback_invoice_amount", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal fallbackInvoiceAmount = new BigDecimal("35.00");

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
