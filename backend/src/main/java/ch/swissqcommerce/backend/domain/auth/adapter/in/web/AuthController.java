package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthUseCase authUseCase;

    @PostMapping("/register")
    public ResponseEntity<UserAccount> register(@RequestBody UserAccount user) {
        return ResponseEntity.ok(authUseCase.register(user));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody UserAccount user) {
        return ResponseEntity.ok("mock-jwt-token");
    }
}
