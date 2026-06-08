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

    private String onboardingStatus = "unapplied";

    private BigDecimal walletBalance = BigDecimal.ZERO;

    private BigDecimal activeLat;

    private BigDecimal activeLng;

    private Integer trustScore = 100;

    private BigDecimal cashCollectedLimit = new BigDecimal("100.00");

    private BigDecimal currentCashInHand = BigDecimal.ZERO;

    private String activeShiftId;

    private OffsetDateTime createdAt;
}