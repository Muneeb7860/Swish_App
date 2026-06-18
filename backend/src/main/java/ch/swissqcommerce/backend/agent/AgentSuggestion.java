package ch.swissqcommerce.backend.agent;

/**
 * Structured output from every agent. This is the contract between the Agent Layer
 * and the Policy Engine. Agents produce these; they never write to the database.
 *
 * @param type       always "suggestion"
 * @param domain     pricing | routing | inventory | risk | support
 * @param action     what the agent recommends
 * @param confidence 0.0–1.0
 * @param reason     why the agent recommends this
 * @param impact     low | medium | high
 */
public record AgentSuggestion(
        String type,
        String domain,
        String action,
        double confidence,
        String reason,
        String impact) {

    /** Convenience factory — locks type to "suggestion". */
    public static AgentSuggestion of(
            String domain, String action, double confidence, String reason, String impact) {
        return new AgentSuggestion("suggestion", domain, action, confidence, reason, impact);
    }
}
