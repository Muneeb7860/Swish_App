package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.LoginRequest;
import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.LoginResponse;
import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.RegisterRequest;
import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.RegisterResponse;
import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthenticationUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.EnrollmentUseCase;
import ch.swissqcommerce.backend.domain.auth.port.out.TokenServicePort;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
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
    private final TokenServicePort tokenServicePort;
    private final UserRepositoryPort userRepositoryPort;

    @Data
    public static class MfaVerifyRequest {
        @JsonProperty("session_token")
        @NotBlank(message = "Session token is required")
        private String sessionToken;

        @JsonProperty("code")
        @NotBlank(message = "OTP code is required")
        private String otpCode;
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
                    "Returns JWT on success. If MFA is enabled, returns mfa_required=true and a"
                            + " session_token instead.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        Session session =
                authenticationUseCase.login(
                        req.getEmail(),
                        req.getPassword(),
                        req.getDeviceFingerprint(),
                        httpReq.getRemoteAddr());
        // Look up the user's role so the JWT carries the correct authority.
        // AuthServiceImpl already validated credentials, so the lookup will succeed.
        String role =
                userRepositoryPort
                        .findByEmail(req.getEmail())
                        .map(UserAccount::getRole)
                        .orElse("CUSTOMER");
        String token = tokenServicePort.generateToken(session.getId(), session.getUserId(), role);
        LoginResponse body =
                LoginResponse.builder()
                        .token(token)
                        .tokenType("Bearer")
                        .sessionId(session.getId())
                        .expiresAt(session.getExpiresAt())
                        .build();
        return ResponseEntity.ok(body);
    }

    @Operation(
            summary = "Verify MFA OTP code",
            description =
                    "Exchanges the session_token + one-time OTP for a full JWT. "
                            + "Call this only when login returned mfa_required=true.")
    @PostMapping("/mfa/verify")
    public ResponseEntity<Map<String, Object>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request) {
        // MFA verification is not yet implemented. Returning 501 to prevent clients
        // from treating any response as a successful authentication. Do NOT return a
        // token (even a mock) here — doing so bypasses MFA entirely.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("error", "MFA verification is not yet implemented."));
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
