package ch.swissqcommerce.backend.domain.auth.core.model;

import lombok.Value;

@Value
public class EmailAddress {
    String value;

    public EmailAddress(String value) {
        if (value == null || !value.contains("@")) {
            throw new IllegalArgumentException("Invalid email address format");
        }
        this.value = value;
    }
}
