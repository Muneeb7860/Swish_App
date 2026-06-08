package ch.swissqcommerce.backend.domain.auth.core.model;

import lombok.Value;

@Value
public class IPAddress {
    String value;

    public IPAddress(String value) {
        this.value = value;
    }
}
