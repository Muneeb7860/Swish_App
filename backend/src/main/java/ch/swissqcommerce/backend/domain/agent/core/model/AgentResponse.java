package ch.swissqcommerce.backend.domain.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResponse {
    private String reply;
    private double confidenceScore;
    private double tokenCost;
    private boolean hitlStatus;
    private String ticketId;
}
