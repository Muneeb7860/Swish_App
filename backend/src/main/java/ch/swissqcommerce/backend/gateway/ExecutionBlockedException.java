package ch.swissqcommerce.backend.gateway;

/**
 * Thrown when the Execution Gateway refuses to execute an action
 * because the Policy Engine did not approve it.
 */
public class ExecutionBlockedException extends RuntimeException {

    private final String policyStatus;

    public ExecutionBlockedException(String policyStatus, String reason) {
        super("Execution blocked [" + policyStatus + "]: " + reason);
        this.policyStatus = policyStatus;
    }

    public String getPolicyStatus() {
        return policyStatus;
    }
}
