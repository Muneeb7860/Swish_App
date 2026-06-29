package ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    /** Present only on a successful full authentication. */
    private String token;
    private String tokenType;
    private String sessionId;
    private OffsetDateTime expiresAt;

    /** Present when the server requires MFA before issuing a JWT. */
    private Boolean mfaRequired;

    /** Opaque token to present to /mfa/verify alongside the OTP. */
    private String sessionToken;

    @com.fasterxml.jackson.annotation.JsonProperty("session_token")
    public String getSessionTokenSnake() {
        return sessionToken;
    }

    /** Compatibility alias for frontend shared-ui which looks for mfaSecret. */
    private String mfaSecret;

    @com.fasterxml.jackson.annotation.JsonProperty("mfa_secret")
    public String getMfaSecretSnake() {
        return mfaSecret;
    }
}
