package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class TelemetryServiceTest {

    @Mock private OrderTelemetryLogRepository telemetryLogRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository riderRepository;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;
    @Mock private ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase ledgerUseCase;

    @InjectMocks
    private TelemetryService telemetryService;

    @Test
    public void testIsThermalBreachActive_NotBreached() {
        // Temperature below 8.0 should not breach
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("7.5"));
        assertFalse(active);
        assertFalse(telemetryService.activeBreaches.containsKey(1));
    }

    @Test
    public void testIsThermalBreachActive_BreachedImmediate() {
        // First tick above 8.0 should trigger alert but not breach yet (since duration is 0)
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("9.0"));
        assertFalse(active);
        assertTrue(telemetryService.activeBreaches.containsKey(1));
    }

    @Test
    public void testIsThermalBreachActive_BreachedAfterTime() {
        // If a breach is active and starts 4 minutes ago, it should be active
        telemetryService.activeBreaches.put(1, OffsetDateTime.now().minusMinutes(4));
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("9.0"));
        assertTrue(active);
        
        // After temperature falls, breach should clear
        boolean activeClear = telemetryService.isThermalBreachActive(1, new BigDecimal("6.0"));
        assertFalse(activeClear);
        assertFalse(telemetryService.activeBreaches.containsKey(1));
    }
}
