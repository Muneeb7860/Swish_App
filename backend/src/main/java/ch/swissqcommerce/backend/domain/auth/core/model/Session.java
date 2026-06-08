package ch.swissqcommerce.backend.domain.auth.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
@Data
@AllArgsConstructor
public class Session {
    private final String id;
    private final String userId;
    private final DeviceFingerprint deviceFingerprint;
    private final IPAddress ipAddress;
    private final OffsetDateTime expiresAt;
    private boolean active;

    public void invalidate() {
        this.active = false;
    }

    public boolean isValid() {
        return this.active && expiresAt.isAfter(OffsetDateTime.now());
    }
}
