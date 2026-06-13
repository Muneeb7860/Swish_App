package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorReading;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.core.service.SensorServiceImpl;
import ch.swissqcommerce.backend.domain.sensor.port.in.SensorUseCase;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

    @Mock private SensorPort port;
    @InjectMocks private SensorServiceImpl service;

    @Test
    void provision_createsProvisionedSensorWithKey() {
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));

        SensorUseCase.ProvisionResult result = service.provision("RTL-1", "store-1", SensorType.TEMPERATURE);

        assertNotNull(result.sensor().getSensorId());
        assertEquals("PROVISIONED", result.sensor().getStatus());
        assertEquals(SensorType.TEMPERATURE, result.sensor().getSensorType());
        assertTrue(result.deviceKey().startsWith("dev_"));
        assertNotNull(result.sensor().getDeviceKeyHash());
        assertNotEquals(result.deviceKey(), result.sensor().getDeviceKeyHash()); // stored hashed, not plaintext
    }

    @Test
    void provision_invalidInputs_rejected() {
        assertThrows(IllegalArgumentException.class, () -> service.provision(" ", "store-1", SensorType.GPS));
        assertThrows(IllegalArgumentException.class, () -> service.provision("RTL-1", "store-1", null));
        verify(port, never()).save(any());
    }

    @Test
    void activate_provisionedBecomesActive() {
        Sensor s = sensor("SNS-1", "PROVISIONED");
        when(port.findById("SNS-1")).thenReturn(Optional.of(s));
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("ACTIVE", service.activate("SNS-1").getStatus());
    }

    @Test
    void activate_decommissioned_rejected() {
        when(port.findById("SNS-2")).thenReturn(Optional.of(sensor("SNS-2", "DECOMMISSIONED")));
        assertThrows(IllegalStateException.class, () -> service.activate("SNS-2"));
    }

    @Test
    void decommission_setsStatus() {
        Sensor s = sensor("SNS-3", "ACTIVE");
        when(port.findById("SNS-3")).thenReturn(Optional.of(s));
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("DECOMMISSIONED", service.decommission("SNS-3").getStatus());
    }

    @Test
    void activate_missingSensor_notFound() {
        when(port.findById("nope")).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class, () -> service.activate("nope"));
    }

    @Test
    void authenticateByDeviceKey_activeOnly() {
        Sensor active = sensor("SNS-4", "ACTIVE");
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.of(active));
        assertTrue(service.authenticateByDeviceKey("dev_x").isPresent());

        active.setStatus("PROVISIONED");
        assertTrue(service.authenticateByDeviceKey("dev_x").isEmpty());
    }

    @Test
    void authenticateByDeviceKey_blank_empty() {
        assertTrue(service.authenticateByDeviceKey("").isEmpty());
        verify(port, never()).findByDeviceKeyHash(any());
    }

    @Test
    void recordReading_activeDevice_writesReading() {
        Sensor active = sensor("SNS-5", "ACTIVE");
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.of(active));
        when(port.saveReading(any())).thenAnswer(i -> i.getArgument(0));

        SensorReading reading = service.recordReading("dev_key", "TEMPERATURE", new BigDecimal("4.5"));

        assertEquals("SNS-5", reading.getSensorId());
        assertEquals("TEMPERATURE", reading.getMetricType());
        assertEquals(0, new BigDecimal("4.5").compareTo(reading.getValue()));
        assertNotNull(reading.getRecordedAt());
    }

    @Test
    void recordReading_invalidOrInactiveKey_denied() {
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,
                () -> service.recordReading("dev_bad", "TEMPERATURE", BigDecimal.ONE));
        verify(port, never()).saveReading(any());
    }

    @Test
    void recordReading_blankMetric_rejected() {
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.of(sensor("SNS-6", "ACTIVE")));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordReading("dev_key", "  ", BigDecimal.ONE));
        verify(port, never()).saveReading(any());
    }

    @Test
    void calibrateSensor_updatesCalibrationStatus() {
        Sensor s = sensor("SNS-7", "ACTIVE");
        when(port.findById("SNS-7")).thenReturn(Optional.of(s));
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));

        Sensor result = service.calibrateSensor("SNS-7", true);
        assertEquals("CALIBRATED", result.getCalibrationStatus());
        assertNotNull(result.getLastCalibratedAt());

        Sensor result2 = service.calibrateSensor("SNS-7", false);
        assertEquals("FAILED", result2.getCalibrationStatus());
    }

    @Test
    void recordReading_calculatesCryptographicHashChain() {
        Sensor active = sensor("SNS-8", "ACTIVE");
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.of(active));
        
        final SensorReading[] saved = new SensorReading[1];
        when(port.saveReading(any())).thenAnswer(i -> {
            saved[0] = i.getArgument(0);
            return saved[0];
        });
        
        when(port.recentReadings("SNS-8")).thenAnswer(i -> {
            if (saved[0] == null) {
                return java.util.Collections.emptyList();
            } else {
                return List.of(saved[0]);
            }
        });

        SensorReading reading1 = service.recordReading("dev_key", "TEMPERATURE", new BigDecimal("4.5"));
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", reading1.getPreviousReadingHash());
        assertNotNull(reading1.getReadingHash());

        SensorReading reading2 = service.recordReading("dev_key", "TEMPERATURE", new BigDecimal("5.0"));
        assertEquals(reading1.getReadingHash(), reading2.getPreviousReadingHash());
        assertNotNull(reading2.getReadingHash());
    }

    @Test
    void verifySensorIntegrity_validAndTamperedChains() {
        Sensor active = sensor("SNS-8", "ACTIVE");
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.of(active));
        
        final SensorReading[] saved = new SensorReading[1];
        when(port.saveReading(any())).thenAnswer(i -> {
            saved[0] = i.getArgument(0);
            return saved[0];
        });
        
        when(port.recentReadings("SNS-8")).thenAnswer(i -> {
            if (saved[0] == null) {
                return java.util.Collections.emptyList();
            } else {
                return List.of(saved[0]);
            }
        });

        SensorReading reading1 = service.recordReading("dev_key", "TEMPERATURE", new BigDecimal("4.5"));
        
        when(port.recentReadings("SNS-8")).thenReturn(List.of(reading1));
        
        assertTrue(service.verifySensorIntegrity("SNS-8"));
        
        reading1.setReadingHash("tampered_hash");
        assertFalse(service.verifySensorIntegrity("SNS-8"));
    }

    @Test
    void verifySensorIntegrity_twoReadingsChain() {
        Sensor active = sensor("SNS-9", "ACTIVE");
        when(port.findByDeviceKeyHash(anyString())).thenReturn(Optional.of(active));
        
        final java.util.List<SensorReading> list = new java.util.ArrayList<>();
        when(port.saveReading(any())).thenAnswer(i -> {
            SensorReading r = i.getArgument(0);
            list.add(r);
            return r;
        });
        
        when(port.recentReadings("SNS-9")).thenAnswer(i -> new java.util.ArrayList<>(list));

        SensorReading reading1 = service.recordReading("dev_key", "TEMPERATURE", new BigDecimal("4.5"));
        SensorReading reading2 = service.recordReading("dev_key", "TEMPERATURE", new BigDecimal("5.0"));
        
        assertTrue(service.verifySensorIntegrity("SNS-9"));
        
        reading2.setPreviousReadingHash("broken_link");
        assertFalse(service.verifySensorIntegrity("SNS-9"));
    }

    private Sensor sensor(String id, String status) {
        return Sensor.builder()
                .sensorId(id).retailerId("RTL-1").storeId("store-1")
                .sensorType(SensorType.TEMPERATURE).status(status).deviceKeyHash("hash").build();
    }
}
