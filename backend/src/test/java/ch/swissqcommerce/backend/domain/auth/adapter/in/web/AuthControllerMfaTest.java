package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.swissqcommerce.backend.domain.auth.adapter.in.web.dto.LoginRequest;
import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthenticationUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.EnrollmentUseCase;
import ch.swissqcommerce.backend.domain.auth.port.in.MfaUseCase;
import ch.swissqcommerce.backend.domain.auth.port.out.TokenServicePort;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Exercises the MFA-enforced path. The default (swish.auth.mfa.enforced=false) used in dev/test
// issues a JWT directly from /login; that flag is enabled here so the challenge flow is asserted.
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "swish.auth.mfa.enforced=true")
public class AuthControllerMfaTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthenticationUseCase authenticationUseCase;

    @MockBean private EnrollmentUseCase enrollmentUseCase;

    @MockBean private MfaUseCase mfaUseCase;

    @MockBean private TokenServicePort tokenServicePort;

    @MockBean private UserRepositoryPort userRepositoryPort;

    @Test
    public void testLoginTriggersMfaAndReturnsSessionToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@swissq.ch");
        req.setPassword("password123");

        Session session =
                Session.builder()
                        .id("session-123")
                        .userId("user-123")
                        .expiresAt(OffsetDateTime.now().plusHours(24))
                        .active(true)
                        .build();

        when(authenticationUseCase.login(eq("test@swissq.ch"), eq("password123"), any(), any()))
                .thenReturn(session);
        when(mfaUseCase.initiateOtp("user-123", "test@swissq.ch")).thenReturn("mfa-token-abc");

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.sessionToken").value("mfa-token-abc"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    public void testVerifyMfaExchangesOtpForJwt() throws Exception {
        AuthController.MfaVerifyRequest verifyRequest = new AuthController.MfaVerifyRequest();
        verifyRequest.setSessionToken("mfa-token-abc");
        verifyRequest.setOtpCode("123456");

        when(mfaUseCase.verifyOtp("mfa-token-abc", "123456")).thenReturn("user-123");
        when(userRepositoryPort.findById("user-123"))
                .thenReturn(Optional.empty()); // default role: CUSTOMER
        when(tokenServicePort.generateToken(anyString(), eq("user-123"), eq("CUSTOMER")))
                .thenReturn("jwt-signed-token");

        mockMvc.perform(
                        post("/api/v1/auth/mfa/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.sessionId").exists());
    }

    @Test
    public void testVerifyMfaWithBadOtpReturnsUnauthorized() throws Exception {
        AuthController.MfaVerifyRequest verifyRequest = new AuthController.MfaVerifyRequest();
        verifyRequest.setSessionToken("mfa-token-abc");
        verifyRequest.setOtpCode("wrong");

        when(mfaUseCase.verifyOtp("mfa-token-abc", "wrong"))
                .thenThrow(new IllegalArgumentException("Invalid OTP code"));

        mockMvc.perform(
                        post("/api/v1/auth/mfa/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isUnauthorized());
    }
}
