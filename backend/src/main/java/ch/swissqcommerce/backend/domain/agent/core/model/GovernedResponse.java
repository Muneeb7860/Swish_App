package ch.swissqcommerce.backend.domain.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of routing a request through the homelab AI-governance pipeline
 * (semantic router + guardrails). Mirrors the Python pipeline's response
 * contract. {@code localOnly} reflects the pipeline's PII gate — the request
 * never left the homelab.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernedResponse {
    /** success | blocked | local_fallback */
    private String status;
    private String reply;
    private String agentId;
    private String intent;
    private boolean localOnly;
    private boolean routedToGovernance;
    private List<String> warnings;
}
