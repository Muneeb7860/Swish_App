package ch.swissqcommerce.backend.domain.dispatch.core.model;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transit_incidents", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransitIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Integer incidentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id")
    private Rider rider;

    @Column(name = "incident_type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String incidentType; // ACCIDENT, BREAKDOWN, WEATHER_HALT

    @Column(name = "gps_latitude", precision = 9, scale = 6, nullable = false)
    @NotNull
    private BigDecimal gpsLatitude;

    @Column(name = "gps_longitude", precision = 9, scale = 6, nullable = false)
    @NotNull
    private BigDecimal gpsLongitude;

    @Column(name = "insurance_claim_registered", nullable = false)
    @Builder.Default
    private Boolean insuranceClaimRegistered = false;

    @Column(name = "status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String status = "REPORTED"; // REPORTED, RESOLVED, CLOSED

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
