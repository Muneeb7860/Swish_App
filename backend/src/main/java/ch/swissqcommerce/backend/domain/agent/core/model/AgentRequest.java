package ch.swissqcommerce.backend.domain.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    private String message;
    private String conversationId;
    private String customerId;
}
