package ch.swissqcommerce.backend.domain.sensor.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sensors", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorEntity {

    @Id
    @Column(name = "sensor_id", length = 50)
    private String sensorId;

    @Column(name = "retailer_id", length = 50)
    private String retailerId;

    @Column(name = "store_id", length = 50)
    private String storeId;

    @Column(name = "sensor_type", length = 20, nullable = false)
    private String sensorType;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "device_key_hash", length = 64)
    private String deviceKeyHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "last_calibrated_at")
    private OffsetDateTime lastCalibratedAt;

    @Column(name = "calibration_status", length = 20)
    private String calibrationStatus;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (calibrationStatus == null) calibrationStatus = "PENDING";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
        if ("ACTIVE".equals(status) && activatedAt == null) {
            activatedAt = OffsetDateTime.now();
        }
    }
}
