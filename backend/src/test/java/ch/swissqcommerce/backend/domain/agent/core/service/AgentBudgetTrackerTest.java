package ch.swissqcommerce.backend.domain.agent.core.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentBudgetTrackerTest {

    private AgentBudgetTracker tracker;

    @BeforeEach
    public void setUp() {
        // Pass null to test without micrometer metrics dependency
        tracker = new AgentBudgetTracker(null);
        tracker.registerMetrics();
    }

    @Test
    public void testInitialStateAndTracking() {
        assertFalse(tracker.isBudgetExceeded());
        assertEquals(0.0, tracker.getDailyCost());
        assertEquals(0, tracker.getHourlyRequestCount());

        tracker.trackUsage(1.50);
        assertEquals(1.50, tracker.getDailyCost());
        assertEquals(1, tracker.getHourlyRequestCount());
        assertFalse(tracker.isBudgetExceeded());

        tracker.trackUsage(3.60);
        assertEquals(5.10, tracker.getDailyCost());
        assertEquals(2, tracker.getHourlyRequestCount());
        assertTrue(tracker.isBudgetExceeded());
    }

    @Test
    public void testMarkDailyBudgetEscalated() {
        assertTrue(tracker.markDailyBudgetEscalated());
        // Second time should return false
        assertFalse(tracker.markDailyBudgetEscalated());
    }

    @Test
    public void testResetDailyCost() {
        tracker.trackUsage(6.00);
        assertTrue(tracker.isBudgetExceeded());

        tracker.resetDailyCost();
        assertEquals(0.0, tracker.getDailyCost());
        assertFalse(tracker.isBudgetExceeded());
    }
}
