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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationUseCase authenticationUseCase;
    private final EnrollmentUseCase enrollmentUseCase;
    private final TokenServicePort tokenServicePort;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        UserAccount user = enrollmentUseCase.register(req.getEmail(), req.getPassword());
        RegisterResponse body = RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmailAddress().getValue())
                .status(user.getStatus().name())
                .build();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest httpReq) {
        Session session = authenticationUseCase.login(
                req.getEmail(),
                req.getPassword(),
                req.getDeviceFingerprint(),
                httpReq.getRemoteAddr()
        );
        String token = tokenServicePort.generateToken(session.getId(), session.getUserId());
        LoginResponse body = LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .sessionId(session.getId())
                .expiresAt(session.getExpiresAt())
                .build();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            authenticationUseCase.logout(sessionId);
        }
        return ResponseEntity.noContent().build();
    }
}
