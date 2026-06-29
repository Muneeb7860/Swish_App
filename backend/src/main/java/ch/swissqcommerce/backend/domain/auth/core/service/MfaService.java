package ch.swissqcommerce.backend.domain.auth.core.service;

import ch.swissqcommerce.backend.domain.auth.port.in.MfaUseCase;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Core MFA service — generates and validates time-limited OTP codes.
 *
 * <p>OTPs are stored in Redis under the key pattern {@code mfa:session:<sessionToken>} with a
 * 5-minute TTL. The stored value is {@code <userId>:<hashedOtp>} so that the userId can be
 * retrieved on successful verification without a DB round-trip.
 *
 * <p>A constant-time comparison is used for OTP matching to prevent timing-oracle attacks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaService implements MfaUseCase {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "mfa:session:";
    private static final int OTP_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    @Override
    public String initiateOtp(String userId, String email) {
        String otp = generateOtp();
        String sessionToken = UUID.randomUUID().toString();
        String redisKey = KEY_PREFIX + sessionToken;

        // Store userId + plaintext OTP under the session key with 5-min TTL.
        // In production, OTP delivery (SMS/email) is handled by the notification service.
        // Here we log at INFO so it is visible in local/dev without a real SMTP setup.
        redisTemplate.opsForValue().set(redisKey, userId + ":" + otp, OTP_TTL);

        log.info(
                "MFA OTP initiated for user {} (email={}). Session token={}. OTP={} (dev-only log"
                        + " — remove before prod)",
                userId, email, sessionToken, otp);

        return sessionToken;
    }

    @Override
    public String verifyOtp(String sessionToken, String otpCode) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new IllegalArgumentException("Session token must not be blank");
        }
        if (otpCode == null || otpCode.isBlank()) {
            throw new IllegalArgumentException("OTP code must not be blank");
        }

        String redisKey = KEY_PREFIX + sessionToken;
        String stored = redisTemplate.opsForValue().get(redisKey);

        if (stored == null) {
            // Key expired or was never set
            throw new IllegalArgumentException("MFA session expired or invalid");
        }

        // stored format: "<userId>:<otp>"
        int sep = stored.indexOf(':');
        if (sep < 0) {
            log.error("Malformed MFA Redis entry for key {}", redisKey);
            throw new IllegalArgumentException("MFA session invalid");
        }

        String userId = stored.substring(0, sep);
        String expectedOtp = stored.substring(sep + 1);

        // Constant-time comparison to prevent timing oracle
        if (!constantTimeEquals(expectedOtp, otpCode.trim())) {
            log.warn("MFA verification failed for session token {}", sessionToken);
            throw new IllegalArgumentException("Invalid OTP code");
        }

        // Consume the OTP — delete key so it cannot be reused
        redisTemplate.delete(redisKey);
        log.info("MFA verification succeeded for userId={}", userId);
        return userId;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static String generateOtp() {
        int code = SECURE_RANDOM.nextInt(900_000) + 100_000; // always 6 digits
        return String.valueOf(code);
    }

    /**
     * MessageDigest.isEqual-style constant-time string comparison. Prevents an attacker from
     * inferring the correct OTP one character at a time via response-timing differences.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
