package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.LoginRequest;
import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.LoginResponse;
import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.RegisterRequest;
import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.RegisterResponse;
import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthenticationUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.EnrollmentUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.MfaUseCase;
import ch.swissqcommerce.backend.domain.auth.port.out.TokenServicePort;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, registration, MFA, and session management")
public class AuthController {

    private final AuthenticationUseCase authenticationUseCase;
    private final EnrollmentUseCase enrollmentUseCase;
    private final MfaUseCase mfaUseCase;
    private final TokenServicePort tokenServicePort;
    private final UserRepositoryPort userRepositoryPort;

    /**
     * Gate for mandatory MFA. Default {@code false} (dev/test): login issues a JWT directly after
     * credential validation, preserving the existing admin/host/customer dev flows and E2E. Set
     * {@code SWISH_MFA_ENFORCED=true} (staging/prod) to require an OTP challenge before the JWT.
     * The MFA verify path is always available regardless of this flag.
     */
    @org.springframework.beans.factory.annotation.Value("${swish.auth.mfa.enforced:false}")
    private boolean mfaEnforced;

    @Data
    public static class MfaVerifyRequest {
        @JsonProperty("session_token")
        private String sessionToken;

        @JsonProperty("code")
        private String otpCode;

        // Frontend shared-ui compatibility fields
        @JsonProperty("mfaSecret")
        private String mfaSecret;

        @JsonProperty("otp")
        private String otp;

        public String getEffectiveSessionToken() {
            if (sessionToken != null && !sessionToken.isBlank()) {
                return sessionToken;
            }
            return mfaSecret;
        }

        public String getEffectiveOtpCode() {
            if (otpCode != null && !otpCode.isBlank()) {
                return otpCode;
            }
            return otp;
        }

        public boolean isValid() {
            String token = getEffectiveSessionToken();
            String code = getEffectiveOtpCode();
            return token != null && !token.isBlank() && code != null && !code.isBlank();
        }
    }

    @Operation(summary = "Register new user account")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        UserAccount user = enrollmentUseCase.register(req.getEmail(), req.getPassword());
        RegisterResponse body =
                RegisterResponse.builder()
                        .userId(user.getId())
                        .email(user.getEmailAddress().getValue())
                        .status(user.getStatus().name())
                        .build();
        return ResponseEntity.ok(body);
    }

    @Operation(
            summary = "Authenticate with username and password",
            description =
                    "Validates credentials. When MFA is enforced (swish.auth.mfa.enforced=true)"
                            + " returns mfa_required=true with a session_token / mfaSecret — POST"
                            + " {session_token, code} to /mfa/verify for the JWT. When MFA is not"
                            + " enforced (dev/test default) returns the JWT directly.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        // 1. Validate credentials — throws on bad password / locked account
        Session session =
                authenticationUseCase.login(
                        req.getEmail(),
                        req.getPassword(),
                        req.getDeviceFingerprint(),
                        httpReq.getRemoteAddr());

        // 2. MFA not enforced (dev/test default): issue the JWT directly so the existing
        //    admin/host/customer login flows and E2E keep working.
        if (!mfaEnforced) {
            String role =
                    userRepositoryPort
                            .findByEmail(req.getEmail())
                            .map(UserAccount::getRole)
                            .orElse("CUSTOMER");
            String token =
                    tokenServicePort.generateToken(session.getId(), session.getUserId(), role);
            return ResponseEntity.ok(
                    LoginResponse.builder()
                            .token(token)
                            .tokenType("Bearer")
                            .sessionId(session.getId())
                            .expiresAt(session.getExpiresAt())
                            .build());
        }

        // 3. MFA enforced (staging/prod): challenge with an OTP stored in Redis (5-min TTL).
        String mfaSessionToken = mfaUseCase.initiateOtp(session.getUserId(), req.getEmail());
        return ResponseEntity.ok(
                LoginResponse.builder()
                        .mfaRequired(true)
                        .sessionToken(mfaSessionToken)
                        .mfaSecret(mfaSessionToken)
                        .build());
    }

    @Operation(
            summary = "Verify MFA OTP code",
            description =
                    "Exchanges the session_token (from /login) + 6-digit OTP for a full JWT. "
                            + "OTP is valid for 5 minutes and is single-use.")
    @PostMapping({"/mfa/verify", "/verify-mfa"})
    public ResponseEntity<LoginResponse> verifyMfa(
            @RequestBody MfaVerifyRequest request) {
        if (request == null || !request.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(LoginResponse.builder().build());
        }

        try {
            // 1. Verify OTP — returns userId on success, throws on wrong/expired
            String userId =
                    mfaUseCase.verifyOtp(request.getEffectiveSessionToken(), request.getEffectiveOtpCode());

            // 2. Fetch role for JWT claim
            String role =
                    userRepositoryPort
                            .findById(userId)
                            .map(UserAccount::getRole)
                            .orElse("CUSTOMER");

            // 3. Issue a fresh fully-authenticated JWT
            String sessionId = UUID.randomUUID().toString();
            String token = tokenServicePort.generateToken(sessionId, userId, role);

            return ResponseEntity.ok(
                    LoginResponse.builder()
                            .token(token)
                            .tokenType("Bearer")
                            .sessionId(sessionId)
                            .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.builder().build()); // empty body; error in header
        }
    }

    @Operation(summary = "Logout and invalidate session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            authenticationUseCase.logout(sessionId);
        }
        return ResponseEntity.noContent().build();
    }
}
