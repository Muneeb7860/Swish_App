package ch.swissqcommerce.backend.domain.agent.port.out;

import ch.swissqcommerce.backend.domain.agent.core.model.GovernedResponse;

/** Outbound port to the homelab AI-governance pipeline (R5 Java↔Python bridge). */
public interface GovernedAgentPort {
    GovernedResponse route(String input, String conversationId);
}
