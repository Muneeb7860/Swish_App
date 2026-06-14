package ch.swissqcommerce.backend.domain.agent.core.service;

import ch.swissqcommerce.backend.domain.agent.port.out.AgentBudgetTrackerPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AgentBudgetTracker implements AgentBudgetTrackerPort {

    private double dailyCost = 0.0;
    private int hourlyRequestCount = 0;
    private boolean dailyBudgetEscalated = false;
    private long lastDailyReset = System.currentTimeMillis();
    private long lastHourlyReset = System.currentTimeMillis();

    private final MeterRegistry meterRegistry;
    private Counter budgetGuardrailCounter;

    public AgentBudgetTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerMetrics() {
        if (meterRegistry != null) {
            budgetGuardrailCounter = Counter.builder("agent.budget.guardrail.triggers")
                    .description("Number of times the AI agent daily cost-budget guardrail was reached")
                    .tag("service", "agent-budget-tracker")
                    .register(meterRegistry);
        }
    }

    private synchronized void checkResets() {
        long now = System.currentTimeMillis();
        if (now - lastDailyReset > 24 * 60 * 60 * 1000) {
            dailyCost = 0.0;
            dailyBudgetEscalated = false;
            lastDailyReset = now;
        }
        if (now - lastHourlyReset > 60 * 60 * 1000) {
            hourlyRequestCount = 0;
            lastHourlyReset = now;
        }
    }

    public synchronized boolean isBudgetExceeded() {
        checkResets();
        return dailyCost >= 5.0;
    }

    public synchronized void trackUsage(double cost) {
        checkResets();
        dailyCost += cost;
        hourlyRequestCount++;
    }

    public synchronized double getDailyCost() {
        checkResets();
        return dailyCost;
    }

    public synchronized int getHourlyRequestCount() {
        checkResets();
        return hourlyRequestCount;
    }

    public synchronized boolean markDailyBudgetEscalated() {
        checkResets();
        if (!dailyBudgetEscalated) {
            dailyBudgetEscalated = true;
            if (budgetGuardrailCounter != null) {
                budgetGuardrailCounter.increment();
            }
            return true;
        }
        return false;
    }

    public synchronized void resetDailyCost() {
        dailyCost = 0.0;
        dailyBudgetEscalated = false;
        lastDailyReset = System.currentTimeMillis();
    }
}
