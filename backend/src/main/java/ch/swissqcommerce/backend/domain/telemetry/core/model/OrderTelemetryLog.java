package ch.swissqcommerce.backend.domain.telemetry.core.model;

import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTelemetryLog {
    private Integer logId;
    private Integer orderId;
    /** Convenience back-reference to the parent Order (not persisted — populated by service). */
    private Order order;
    private OffsetDateTime deviceTimestamp;
    private OffsetDateTime serverTimestamp;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal temperature;
    @Builder.Default
    private Boolean dryIceInjected = false;
    @Builder.Default
    private Boolean alertTriggered = false;
}
