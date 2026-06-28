package ch.swissqcommerce.backend.domain.auth.core.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

public class MfaServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MfaService mfaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        mfaService = new MfaService(redisTemplate);
    }

    @Test
    void testInitiateOtpStoresCorrectFormatAndTtl() {
        String userId = "user-123";
        String email = "test@swissq.ch";

        String token = mfaService.initiateOtp(userId, email);

        assertNotNull(token);
        assertFalse(token.isBlank());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertEquals("mfa:session:" + token, keyCaptor.getValue());
        assertTrue(valueCaptor.getValue().startsWith(userId + ":"));
        assertEquals(Duration.ofMinutes(5), ttlCaptor.getValue());

        String otp = valueCaptor.getValue().substring(userId.length() + 1);
        assertEquals(6, otp.length());
        assertTrue(otp.matches("\\d{6}"));
    }

    @Test
    void testVerifyOtpSucceedsWithCorrectCode() {
        String sessionToken = "session-token-123";
        String userId = "user-123";
        String otp = "123456";
        String redisKey = "mfa:session:" + sessionToken;

        when(valueOperations.get(redisKey)).thenReturn(userId + ":" + otp);

        String resultUserId = mfaService.verifyOtp(sessionToken, otp);

        assertEquals(userId, resultUserId);
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    void testVerifyOtpFailsWithIncorrectCode() {
        String sessionToken = "session-token-123";
        String userId = "user-123";
        String otp = "123456";
        String redisKey = "mfa:session:" + sessionToken;

        when(valueOperations.get(redisKey)).thenReturn(userId + ":" + otp);

        assertThrows(IllegalArgumentException.class, () -> {
            mfaService.verifyOtp(sessionToken, "654321");
        });

        // Key should NOT be deleted on failure (allows retry within window)
        verify(redisTemplate, never()).delete(redisKey);
    }

    @Test
    void testVerifyOtpFailsWhenExpiredOrMissing() {
        String sessionToken = "session-token-123";
        String redisKey = "mfa:session:" + sessionToken;

        when(valueOperations.get(redisKey)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            mfaService.verifyOtp(sessionToken, "123456");
        });
    }
}
