package ch.swissqcommerce.backend.domain.auth.core.model;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
