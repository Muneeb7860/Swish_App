package ch.swissqcommerce.backend.domain.auth.core.model;

public record LoginResponse(boolean mfaRequired, String sessionToken, String token) {}
