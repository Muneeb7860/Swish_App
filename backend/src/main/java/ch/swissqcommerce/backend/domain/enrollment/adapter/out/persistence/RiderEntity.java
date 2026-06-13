package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "riders", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiderEntity {

    @Id
    @Column(name = "rider_id", length = 50)
    @Size(max = 50)
    private String riderId;

    @Column(name = "full_name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Column(name = "vehicle_type", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String vehicleType;

    @Column(name = "onboarding_status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String onboardingStatus = "unapplied";

    @Column(name = "wallet_balance", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(name = "active_lat", precision = 9, scale = 6)
    private BigDecimal activeLat;

    @Column(name = "active_lng", precision = 9, scale = 6)
    private BigDecimal activeLng;

    @Column(name = "trust_score", nullable = false)
    @Min(0)
    @Max(100)
    @Builder.Default
    private Integer trustScore = 100;

    @Column(name = "cash_collected_limit", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal cashCollectedLimit = new BigDecimal("100.00");

    @Column(name = "current_cash_in_hand", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal currentCashInHand = BigDecimal.ZERO;

    @Column(name = "active_shift_id", length = 50)
    @Size(max = 50)
    private String activeShiftId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "gear_exempt", nullable = false)
    @Builder.Default
    private boolean gearExempt = false;
}
