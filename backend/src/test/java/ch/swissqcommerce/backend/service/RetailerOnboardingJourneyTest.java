package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.port.in.BillingUseCase;
import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;
import ch.swissqcommerce.backend.domain.retailer.core.service.RetailerServiceImpl;
import ch.swissqcommerce.backend.domain.retailer.port.in.RetailerUseCase;
import ch.swissqcommerce.backend.domain.retailer.port.out.RetailerPort;
import ch.swissqcommerce.backend.domain.sensor.core.model.Sensor;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorReading;
import ch.swissqcommerce.backend.domain.sensor.core.model.SensorType;
import ch.swissqcommerce.backend.domain.sensor.core.service.SensorServiceImpl;
import ch.swissqcommerce.backend.domain.sensor.port.in.SensorUseCase;
import ch.swissqcommerce.backend.domain.sensor.port.out.SensorPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Cross-domain onboarding journey for BRD FR-01 (retailer + sensor).
 *
 * <p>Proves the full backend flow end-to-end — self-service registration →
 * sequential 3-gate approval → one-time API key → sensor provisioning →
 * activation → key/device authentication — driving the real
 * {@link RetailerServiceImpl} and {@link SensorServiceImpl} over stateful
 * in-memory ports (fast and deterministic, no Spring context). Also pins the new
 * PENDING approval-queue listing. A full {@code @SpringBootTest} + MockMvc variant
 * could additionally cover the controllers' {@code @PreAuthorize} gates.
 */
class RetailerOnboardingJourneyTest {

    /** Stateful in-memory RetailerPort. */
    private static final class InMemoryRetailerPort implements RetailerPort {
        private final Map<String, Retailer> store = new LinkedHashMap<>();

        @Override
        public Retailer save(Retailer r) {
            store.put(r.getRetailerId(), r);
            return r;
        }

        @Override
        public Optional<Retailer> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Retailer> findByApiKeyHash(String hash) {
            return store.values().stream()
                    .filter(r -> hash != null && hash.equals(r.getApiKeyHash()))
                    .findFirst();
        }

        @Override
        public List<Retailer> findByStatus(String status) {
            return store.values().stream().filter(r -> status.equals(r.getStatus())).toList();
        }
    }

    /** Stateful in-memory SensorPort (readings unused by this journey). */
    private static final class InMemorySensorPort implements SensorPort {
        private final Map<String, Sensor> store = new LinkedHashMap<>();

        @Override
        public Sensor save(Sensor s) {
            store.put(s.getSensorId(), s);
            return s;
        }

        @Override
        public Optional<Sensor> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Sensor> findByDeviceKeyHash(String hash) {
            return store.values().stream()
                    .filter(s -> hash != null && hash.equals(s.getDeviceKeyHash()))
                    .findFirst();
        }

        @Override
        public List<Sensor> findByRetailerId(String retailerId) {
            return store.values().stream().filter(s -> retailerId.equals(s.getRetailerId())).toList();
        }

        @Override
        public List<Sensor> findByStoreId(String storeId) {
            return store.values().stream().filter(s -> storeId.equals(s.getStoreId())).toList();
        }

        @Override
        public SensorReading saveReading(SensorReading reading) {
            return reading;
        }

        @Override
        public List<SensorReading> recentReadings(String sensorId) {
            return new ArrayList<>();
        }
    }

    @Test
    void fullOnboardingJourney_registerToActiveSensor() {
        InMemoryRetailerPort retailerPort = new InMemoryRetailerPort();
        InMemorySensorPort sensorPort = new InMemorySensorPort();

        BillingAccount account = mock(BillingAccount.class);
        when(account.getAccountId()).thenReturn("BILL-FR01");
        BillingUseCase billing = mock(BillingUseCase.class);
        when(billing.subscribe(anyString(), any())).thenReturn(account);

        RetailerUseCase retailers = new RetailerServiceImpl(retailerPort, billing);
        SensorUseCase sensors = new SensorServiceImpl(sensorPort);

        // 1. Self-service registration → PENDING, visible in the approval queue.
        Retailer r = retailers.register("Valora k-kiosk", "ops@valora.ch", "ZRH-HB", BillingTier.PRO);
        String id = r.getRetailerId();
        assertEquals("PENDING", r.getStatus());
        assertTrue(
                retailers.listByStatus("PENDING").stream()
                        .anyMatch(x -> x.getRetailerId().equals(id)));

        // 2. Sequential gates — no API key is issued until fully approved.
        assertNull(retailers.approveGate(id, "ops").issuedApiKey());
        assertNull(retailers.approveGate(id, "compliance").issuedApiKey());
        RetailerUseCase.ApprovalResult activation = retailers.approveGate(id, "admin");

        // 3. Activation issues the key exactly once; retailer leaves the PENDING queue.
        String apiKey = activation.issuedApiKey();
        assertNotNull(apiKey);
        assertEquals("ACTIVE", activation.retailer().getStatus());
        assertEquals("BILL-FR01", activation.retailer().getBillingAccountId());
        assertTrue(retailers.listByStatus("PENDING").isEmpty());

        // 4. The issued key authenticates the now-active retailer.
        assertEquals(
                id, retailers.authenticateByApiKey(apiKey).orElseThrow().getRetailerId());

        // 5. Provision a sensor for the retailer's store.
        SensorUseCase.ProvisionResult prov =
                sensors.provision(id, "ZRH-HB", SensorType.TEMPERATURE);
        String deviceKey = prov.deviceKey();
        String sensorId = prov.sensor().getSensorId();
        assertNotNull(deviceKey);
        assertEquals("PROVISIONED", prov.sensor().getStatus());
        assertTrue(
                sensors.listByRetailer(id).stream().anyMatch(s -> s.getSensorId().equals(sensorId)));

        // 6. The device key only authenticates once the sensor is ACTIVE.
        assertTrue(sensors.authenticateByDeviceKey(deviceKey).isEmpty());
        sensors.activate(sensorId);
        assertEquals(
                id, sensors.authenticateByDeviceKey(deviceKey).orElseThrow().getRetailerId());
    }

    @Test
    void listByStatus_blankStatus_rejected() {
        RetailerUseCase retailers =
                new RetailerServiceImpl(new InMemoryRetailerPort(), mock(BillingUseCase.class));
        assertThrows(IllegalArgumentException.class, () -> retailers.listByStatus("  "));
    }
}
