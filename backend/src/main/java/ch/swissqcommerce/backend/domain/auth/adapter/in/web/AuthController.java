package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.domain.auth.core.model.LoginResponse;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*") // Allow frontend integration calls
public class AuthController {

    @Autowired
    private AuthService authService;

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class VerifyMfaRequest {
        @NotBlank(message = "Session token is required")
        private String sessionToken;

        @NotBlank(message = "MFA code is required")
        private String code;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<?> verifyMfa(@Valid @RequestBody VerifyMfaRequest request) {
        try {
            String token = authService.verifyMfa(request.getSessionToken(), request.getCode());
            return ResponseEntity.ok(Map.of("token", token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}
