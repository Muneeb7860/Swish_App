package ch.swissqcommerce.backend.domain.enrollment.core.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rider {

    private String riderId;

    private String fullName;

    private String vehicleType;

    @Builder.Default private String onboardingStatus = "unapplied";

    @Builder.Default private BigDecimal walletBalance = BigDecimal.ZERO;

    private BigDecimal activeLat;

    private BigDecimal activeLng;

    @Builder.Default private Integer trustScore = 100;

    @Builder.Default private BigDecimal cashCollectedLimit = new BigDecimal("100.00");

    @Builder.Default private BigDecimal currentCashInHand = BigDecimal.ZERO;

    private String activeShiftId;

    private OffsetDateTime createdAt;

    @Builder.Default private boolean gearExempt = false;
}
