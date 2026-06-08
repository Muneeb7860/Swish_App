package ch.swissqcommerce.backend.domain.dispatch.core.model;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitIncident {

    private Integer incidentId;

    @NotNull
    private Order order;

    private Rider rider;

    @NotBlank
    @Size(max = 20)
    private String incidentType; // ACCIDENT, BREAKDOWN, WEATHER_HALT

    @NotNull
    private BigDecimal gpsLatitude;

    @NotNull
    private BigDecimal gpsLongitude;

    @Builder.Default
    private Boolean insuranceClaimRegistered = false;

    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "REPORTED"; // REPORTED, RESOLVED, CLOSED

    private OffsetDateTime createdAt;
}
