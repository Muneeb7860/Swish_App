package ch.swissqcommerce.backend.domain.auth.core.model;

import lombok.Value;

@Value
public class DeviceFingerprint {
    String value;

    public DeviceFingerprint(String value) {
        this.value = value;
    }
}
