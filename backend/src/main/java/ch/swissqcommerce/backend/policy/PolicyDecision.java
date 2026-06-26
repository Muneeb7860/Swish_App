package ch.swissqcommerce.backend.policy;

/**
 * Output of the Policy Engine. Determines whether an agent suggestion is allowed to proceed to the
 * Execution Gateway.
 *
 * @param status approved | rejected | needs_human | expired
 * @param modifiedAction optional override of the original action
 * @param reason why this decision was made
 */
public record PolicyDecision(
        String status, String modifiedAction, String reason, String assigneeRole) {

    public PolicyDecision(String status, String modifiedAction, String reason) {
        this(status, modifiedAction, reason, null);
    }

    public static PolicyDecision approved(String reason) {
        return new PolicyDecision("approved", null, reason, null);
    }

    public static PolicyDecision rejected(String reason) {
        return new PolicyDecision("rejected", null, reason, null);
    }

    public static PolicyDecision needsHuman(String reason) {
        return new PolicyDecision("needs_human", null, reason, null);
    }

    public static PolicyDecision expired(String reason) {
        return new PolicyDecision("expired", null, reason, null);
    }

    public PolicyDecision withAssigneeRole(String role) {
        return new PolicyDecision(this.status, this.modifiedAction, this.reason, role);
    }

    public boolean isApproved() {
        return "approved".equals(status);
    }
}
