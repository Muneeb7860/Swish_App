package ch.swissqcommerce.backend.model;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "order_telemetry_logs", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTelemetryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(name = "device_timestamp", nullable = false)
    @NotNull
    private OffsetDateTime deviceTimestamp;

    @Column(name = "server_timestamp", insertable = false, updatable = false)
    private OffsetDateTime serverTimestamp;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
    @NotNull
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
    @NotNull
    private BigDecimal longitude;

    @Column(name = "temperature", precision = 4, scale = 1, nullable = false)
    @NotNull
    private BigDecimal temperature;

    @Column(name = "dry_ice_injected", nullable = false)
    @Builder.Default
    private Boolean dryIceInjected = false;

    @Column(name = "alert_triggered", nullable = false)
    @Builder.Default
    private Boolean alertTriggered = false;
}

