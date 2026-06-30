package ch.swissqcommerce.backend.domain.logistics.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.*;

/**
 * JPA entity for {@code oltp.carrier_sla}. Stores per-carrier delivery time windows, weight
 * capacity, and fragile-item handling capability for RoutingAgent v1.0 SLA filtering.
 */
@Entity
@Table(name = "carrier_sla", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarrierSla {

    @Id
    @Column(name = "carrier", length = 50)
    @Size(max = 50)
    private String carrier;

    /** Maximum single-package weight in kilograms. */
    @Column(name = "max_weight_kg", nullable = false, precision = 6, scale = 2)
    @NotNull
    @DecimalMin("0.0")
    @Builder.Default
    private BigDecimal maxWeightKg = new BigDecimal("30.0");

    /** Standard delivery window in business days. */
    @Column(name = "standard_days", nullable = false)
    @NotNull
    @Min(1)
    @Builder.Default
    private Integer standardDays = 5;

    /** Express / expedited delivery window in business days. */
    @Column(name = "express_days", nullable = false)
    @NotNull
    @Min(1)
    @Builder.Default
    private Integer expressDays = 2;

    /** Whether this carrier accepts fragile/high-value items. */
    @Column(name = "fragile_ok", nullable = false)
    @NotNull
    @Builder.Default
    private Boolean fragileOk = false;

    /** Whether this SLA rule is currently active. */
    @Column(name = "active", nullable = false)
    @NotNull
    @Builder.Default
    private Boolean active = true;
}
