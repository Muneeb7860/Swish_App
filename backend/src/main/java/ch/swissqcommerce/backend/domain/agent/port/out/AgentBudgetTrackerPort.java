package ch.swissqcommerce.backend.domain.agent.port.out;

public interface AgentBudgetTrackerPort {
    boolean isBudgetExceeded();

    void trackUsage(double cost);

    double getDailyCost();

    int getHourlyRequestCount();

    boolean markDailyBudgetEscalated();

    void resetDailyCost();
}
