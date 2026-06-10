package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.auth.core.model.LoginResponse;
import ch.swissqcommerce.backend.domain.auth.core.service.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private Environment env;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String JWT_SECRET = "mysupersecretjwtkeysecurityteststring256bits";

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", 3600000L); // 1 hour
        ReflectionTestUtils.setField(authService, "mfaOtpExpirationSec", 300L);   // 5 min
        lenient().when(env.getActiveProfiles()).thenReturn(new String[]{"default"});
    }

    @Test
    public void testLogin_AdminSuccessMfaRequired() {
        LoginResponse response = authService.login("swissadmin", "adminpassword");
        
        assertNotNull(response);
        assertTrue(response.mfaRequired());
        assertNotNull(response.sessionToken());
        assertNull(response.token());
    }

    @Test
    public void testLogin_UserSuccessMfaRequired() {
        LoginResponse response = authService.login("swissuser", "userpassword");
        
        assertNotNull(response);
        assertTrue(response.mfaRequired());
        assertNotNull(response.sessionToken());
        assertNull(response.token());
    }

    @Test
    public void testLogin_InvalidUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            authService.login("invaliduser", "userpassword");
        });
    }

    @Test
    public void testLogin_InvalidPassword() {
        assertThrows(IllegalArgumentException.class, () -> {
            authService.login("swissuser", "wrongpassword");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            authService.login("swissuser", null);
        });
    }

    @Test
    public void testVerifyMfa_Success() {
        // Init login session
        LoginResponse response = authService.login("swissadmin", "adminpassword");
        String sessionToken = response.sessionToken();
        
        // Extract internal OTP code for test verification
        Object mfaSessionMapObj = ReflectionTestUtils.getField(authService, "mfaSessions");
        assertNotNull(mfaSessionMapObj);
        java.util.Map<String, ?> mfaSessions = (java.util.Map<String, ?>) mfaSessionMapObj;
        Object session = mfaSessions.get(sessionToken);
        assertNotNull(session);
        String otpCode = (String) ReflectionTestUtils.getField(session, "code");
        assertNotNull(otpCode);

        // Verify MFA code
        String jwtToken = authService.verifyMfa(sessionToken, otpCode);
        assertNotNull(jwtToken);
        
        // Session should be removed after verification
        assertNull(mfaSessions.get(sessionToken));
    }

    @Test
    public void testVerifyMfa_InvalidSessionToken() {
        assertThrows(IllegalArgumentException.class, () -> {
            authService.verifyMfa("invalid_session", "123456");
        });
    }

    @Test
    public void testVerifyMfa_InvalidPasscode() {
        LoginResponse response = authService.login("swissadmin", "adminpassword");
        String sessionToken = response.sessionToken();

        assertThrows(IllegalArgumentException.class, () -> {
            authService.verifyMfa(sessionToken, "999999"); // Wrong code
        });
    }

    @Test
    public void testVerifyMfa_ExpiredSession() {
        // Set short expiration
        ReflectionTestUtils.setField(authService, "mfaOtpExpirationSec", -10L); // Already expired
        
        LoginResponse response = authService.login("swissadmin", "adminpassword");
        String sessionToken = response.sessionToken();
        
        Object mfaSessionMapObj = ReflectionTestUtils.getField(authService, "mfaSessions");
        java.util.Map<String, ?> mfaSessions = (java.util.Map<String, ?>) mfaSessionMapObj;
        Object session = mfaSessions.get(sessionToken);
        String otpCode = (String) ReflectionTestUtils.getField(session, "code");

        assertThrows(IllegalArgumentException.class, () -> {
            authService.verifyMfa(sessionToken, otpCode);
        });

        // Expired session should be removed
        assertNull(mfaSessions.get(sessionToken));
    }
}
