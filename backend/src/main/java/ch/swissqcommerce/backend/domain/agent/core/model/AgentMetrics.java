package ch.swissqcommerce.backend.domain.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMetrics {
    private double dailyCost;
    private int hourlyRequestCount;
    private double dailyBudgetLimit;
    private int hourlyRequestLimit;
}
