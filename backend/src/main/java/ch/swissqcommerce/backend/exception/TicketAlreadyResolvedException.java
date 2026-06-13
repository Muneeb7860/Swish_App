package ch.swissqcommerce.backend.exception;

/**
 * Thrown when a HITL governance ticket that is no longer {@code PENDING} (already
 * APPROVED/REJECTED) is submitted for approval or rejection again.
 *
 * <p>Maps to HTTP 409 Conflict so a duplicate approve/reject is an idempotent no-op on the ticket
 * state rather than a re-processed override.
 */
public class TicketAlreadyResolvedException extends BusinessException {
    public TicketAlreadyResolvedException(String message) {
        super(message);
    }
}
