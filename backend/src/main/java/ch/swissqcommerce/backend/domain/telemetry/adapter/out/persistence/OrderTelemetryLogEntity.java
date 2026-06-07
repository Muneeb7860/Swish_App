package ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "order_telemetry_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTelemetryLogEntityEntity {
    @Id
    private Integer logId;
    private Integer orderId;
    private BigDecimal lat;
    private BigDecimal lng;
    private BigDecimal tempCelsius;
    private String riderId;
    private OffsetDateTime deviceTimestamp;
    private OffsetDateTime serverTimestamp;
}
