package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthenticationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, registration, MFA, and session management")
public class AuthController {
    private final AuthenticationUseCase authenticationUseCase;
    private final ch.swissqcommerce.backend.domain.auth.port.in.EnrollmentUseCase enrollmentUseCase;

    @Data
    public static class MfaVerifyRequest {
        @NotBlank(message = "Session token is required")
        private String sessionToken;
        @NotBlank(message = "OTP code is required")
        private String otpCode;
    }

    @Operation(summary = "Register new user account")
    @PostMapping("/register")
    public ResponseEntity<UserAccount> register(@RequestBody UserAccount user) {
        return ResponseEntity.ok(enrollmentUseCase.register(user.getEmailAddress().getValue(), user.getPasswordHash().getValue()));
    }

    @Operation(summary = "Authenticate with username and password",
               description = "Returns JWT on success. If MFA is enabled, returns mfa_required=true and a session_token instead.")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody UserAccount user) {
        return ResponseEntity.ok(Map.of(
                "token", "mock-jwt-token",
                "mfa_required", false
        ));
    }

    @Operation(summary = "Verify MFA OTP code",
               description = "Exchanges the session_token + one-time OTP for a full JWT. " +
                             "Call this only when login returned mfa_required=true.")
    @PostMapping("/mfa/verify")
    public ResponseEntity<Map<String, Object>> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        // Delegate to auth use-case when MFA is fully implemented
        return ResponseEntity.ok(Map.of(
                "token", "mock-jwt-token-after-mfa",
                "expires_in", 86400
        ));
    }

    @Operation(summary = "Logout and invalidate session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return ResponseEntity.noContent().build();
    }
}
