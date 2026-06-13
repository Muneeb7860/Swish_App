package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps the {@code oltp.sensor_readings} TimescaleDB hypertable. Hibernate's single-column @Id
 * (reading_id) validates fine against the table's composite PK (reading_id, recorded_at) — the
 * validator checks column presence, not PK shape — so the same entity works on H2 (tests) and
 * TimescaleDB (prod/CI).
 */
@Entity
@Table(name = "sensor_readings", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reading_id")
    private Long readingId;

    @Column(name = "sensor_id", length = 50, nullable = false)
    private String sensorId;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "metric_type", length = 20, nullable = false)
    private String metricType;

    @Column(name = "reading_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal readingValue;

    @Column(name = "previous_reading_hash", length = 64)
    private String previousReadingHash;

    @Column(name = "reading_hash", length = 64)
    private String readingHash;
}
