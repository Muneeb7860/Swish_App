package ch.swissqcommerce.backend.domain.auth.core.model;

import lombok.Value;

@Value
public class PasswordHash {
    String value;

    public PasswordHash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be empty");
        }
        this.value = value;
    }
}
