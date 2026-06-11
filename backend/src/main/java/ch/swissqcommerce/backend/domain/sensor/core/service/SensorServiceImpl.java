package ch.swissqcommerce.backend.domain.sensor.core.service;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.port.in.SensorUseCase;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SensorServiceImpl implements SensorUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SensorPort port;

    @Override
    @Transactional
    public ProvisionResult provision(String retailerId, String storeId, SensorType type) {
        if (retailerId == null || retailerId.isBlank()) throw new IllegalArgumentException("retailerId is required");
        if (type == null) throw new IllegalArgumentException("sensor type is required");

        String deviceKey = generateDeviceKey();
        Sensor sensor = Sensor.builder()
                .sensorId("SNS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
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
