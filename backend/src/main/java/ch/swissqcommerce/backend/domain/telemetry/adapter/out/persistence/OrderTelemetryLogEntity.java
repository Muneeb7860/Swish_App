package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "order_telemetry_logs", schema = "oltp")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTelemetryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "device_timestamp", nullable = false)
    private OffsetDateTime deviceTimestamp;

    @Column(name = "server_timestamp", insertable = false, updatable = false)
    private OffsetDateTime serverTimestamp;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitude;

    @Column(name = "temperature", precision = 4, scale = 1, nullable = false)
    private BigDecimal temperature;

    @Column(name = "dry_ice_injected", nullable = false)
    @Builder.Default
    private Boolean dryIceInjected = false;

    @Column(name = "alert_triggered", nullable = false)
    @Builder.Default
    private Boolean alertTriggered = false;
}
