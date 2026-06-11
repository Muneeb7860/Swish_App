package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.core.service.SensorServiceImpl;
import ch.swissqcommerce.backend.domain.sensor.port.in.SensorUseCase;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private Sensor sensor(String id, String status) {
        return Sensor.builder()
                .sensorId(id).retailerId("RTL-1").storeId("store-1")
                .sensorType(SensorType.TEMPERATURE).status(status).deviceKeyHash("hash").build();
    }
}
