package ch.swissqcommerce.backend.domain.sensor.core.service;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorReading;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.port.in.SensorUseCase;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensorServiceImpl implements SensorUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SensorPort port;

    @Override
    @Transactional
    public ProvisionResult provision(String retailerId, String storeId, SensorType type) {
        if (retailerId == null || retailerId.isBlank())
            throw new IllegalArgumentException("retailerId is required");
        if (type == null) throw new IllegalArgumentException("sensor type is required");

        String deviceKey = generateDeviceKey();
        Sensor sensor =
                Sensor.builder()
                        .sensorId(
                                "SNS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .retailerId(retailerId)
                        .storeId(storeId)
                        .sensorType(type)
                        .status("PROVISIONED")
                        .deviceKeyHash(sha256(deviceKey))
                        .build();
        return new ProvisionResult(port.save(sensor), deviceKey);
    }

    @Override
    @Transactional
    public Sensor activate(String sensorId) {
        Sensor sensor = require(sensorId);
        if ("DECOMMISSIONED".equals(sensor.getStatus())) {
            throw new IllegalStateException("Cannot activate a decommissioned sensor");
        }
        sensor.setStatus("ACTIVE");
        return port.save(sensor);
    }

    @Override
    @Transactional
    public Sensor decommission(String sensorId) {
        Sensor sensor = require(sensorId);
        sensor.setStatus("DECOMMISSIONED");
        return port.save(sensor);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sensor> getSensor(String sensorId) {
        return port.findById(sensorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sensor> listByRetailer(String retailerId) {
        return port.findByRetailerId(retailerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sensor> authenticateByDeviceKey(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) return Optional.empty();
        return port.findByDeviceKeyHash(sha256(deviceKey))
                .filter(s -> "ACTIVE".equals(s.getStatus()));
    }

    @Override
    @Transactional
    public SensorReading recordReading(String deviceKey, String metricType, BigDecimal value) {
        Sensor sensor =
                authenticateByDeviceKey(deviceKey)
                        .orElseThrow(
                                () -> new AccessDeniedException("Invalid or inactive device key"));
        if (metricType == null || metricType.isBlank())
            throw new IllegalArgumentException("metricType is required");
        if (value == null) throw new IllegalArgumentException("value is required");

        List<SensorReading> recent = port.recentReadings(sensor.getSensorId());
        String prevHash = "0000000000000000000000000000000000000000000000000000000000000000";
        if (recent != null && !recent.isEmpty()) {
            SensorReading lastReading = recent.get(0);
            if (lastReading.getReadingHash() != null) {
                prevHash = lastReading.getReadingHash();
            }
        }

        OffsetDateTime recordedAt = OffsetDateTime.now();
        String currentHash =
                computeReadingHash(sensor.getSensorId(), recordedAt, metricType, value, prevHash);

        SensorReading reading =
                SensorReading.builder()
                        .sensorId(sensor.getSensorId())
                        .recordedAt(recordedAt)
                        .metricType(metricType)
                        .value(value)
                        .previousReadingHash(prevHash)
                        .readingHash(currentHash)
                        .build();
        return port.saveReading(reading);
    }

    private String computeReadingHash(
            String sensorId,
            OffsetDateTime recordedAt,
            String metricType,
            BigDecimal value,
            String prevHash) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String data =
                    sensorId
                            + ":"
                            + recordedAt.toString()
                            + ":"
                            + metricType
                            + ":"
                            + value.toString()
                            + ":"
                            + prevHash;
            return HexFormat.of().formatHex(md.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    @Transactional
    public Sensor calibrateSensor(String sensorId, boolean success) {
        Sensor sensor = require(sensorId);
        sensor.setLastCalibratedAt(OffsetDateTime.now());
        sensor.setCalibrationStatus(success ? "CALIBRATED" : "FAILED");
        return port.save(sensor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SensorReading> getRecentReadings(String sensorId) {
        return port.recentReadings(sensorId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifySensorIntegrity(String sensorId) {
        List<SensorReading> readings = port.recentReadings(sensorId);
        if (readings == null || readings.isEmpty()) {
            return true;
        }
        List<SensorReading> sorted = new java.util.ArrayList<>(readings);
        sorted.sort(java.util.Comparator.comparing(SensorReading::getRecordedAt));
        for (int i = 0; i < sorted.size(); i++) {
            SensorReading current = sorted.get(i);
            String calculatedHash =
                    computeReadingHash(
                            current.getSensorId(),
                            current.getRecordedAt(),
                            current.getMetricType(),
                            current.getValue(),
                            current.getPreviousReadingHash());
            if (!calculatedHash.equals(current.getReadingHash())) {
                return false;
            }
            if (i > 0) {
                SensorReading prev = sorted.get(i - 1);
                if (!current.getPreviousReadingHash().equals(prev.getReadingHash())) {
                    return false;
                }
            }
        }
        return true;
    }

    private Sensor require(String sensorId) {
        return port.findById(sensorId)
                .orElseThrow(() -> new NoSuchElementException("Sensor not found: " + sensorId));
    }

    private String generateDeviceKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "dev_" + HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
