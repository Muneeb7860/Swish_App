package ch.swissqcommerce.backend.domain.sensor.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A single time-series telemetry reading emitted by a provisioned sensor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorReading {
    private Long readingId;
    private String sensorId;
    private OffsetDateTime recordedAt;
    private String metricType;
    private BigDecimal value;
    private String previousReadingHash;
    private String readingHash;
}
