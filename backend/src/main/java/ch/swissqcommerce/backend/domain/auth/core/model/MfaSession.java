package ch.swissqcommerce.backend.domain.auth.core.model;

import java.time.Instant;

public record MfaSession(
    String username,
    String code,
    Instant expiryTime
) {}
