package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.telemetry.core.service.TelemetryServiceImpl;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.repository.*;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
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

    @Mock private TelemetryPort telemetryPort;
    @Mock private OrderRepository orderRepository;
    @Mock private RiderRepository riderRepository;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;
    @Mock private LedgerUseCase ledgerUseCase;

    @InjectMocks
    private TelemetryServiceImpl telemetryService;

    @Test
    public void testIsThermalBreachActive_NotBreached() {
        // Temperature below 8.0 should not breach
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("7.5"));
        assertFalse(active);
    }

    @Test
    public void testIsThermalBreachActive_BreachedImmediate() {
        // First tick above 8.0 should trigger alert but not breach yet (since duration is 0)
        boolean active = telemetryService.isThermalBreachActive(1, new BigDecimal("9.0"));
        assertFalse(active);
    }
}
