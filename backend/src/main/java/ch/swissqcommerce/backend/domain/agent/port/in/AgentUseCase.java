package ch.swissqcommerce.backend.domain.agent.port.in;

import ch.swissqcommerce.backend.domain.agent.core.model.AgentRequest;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentResponse;
import ch.swissqcommerce.backend.domain.agent.core.model.AgentMetrics;

public interface AgentUseCase {
    AgentResponse processMessage(AgentRequest request);
    AgentMetrics getMetrics();
}

