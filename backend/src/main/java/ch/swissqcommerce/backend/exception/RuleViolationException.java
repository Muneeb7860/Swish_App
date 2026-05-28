package ch.swissqcommerce.backend.exception;

public class RuleViolationException extends BusinessException {
    public RuleViolationException(String message) {
        super(message);
    }
}
