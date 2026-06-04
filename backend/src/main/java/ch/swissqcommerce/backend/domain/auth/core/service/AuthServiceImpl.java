package ch.swissqcommerce.backend.domain.auth.core.service;

import ch.swissqcommerce.backend.domain.auth.core.model.LoginResponse;
import ch.swissqcommerce.backend.domain.auth.core.model.MfaSession;
import ch.swissqcommerce.backend.domain.auth.port.in.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    @Autowired
    private Environment env;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${mfa.otp.expiration-sec}")
    private long mfaOtpExpirationSec;

    private final Map<String, MfaSession> mfaSessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public LoginResponse login(String username, String password) {
        if (!"swissadmin".equalsIgnoreCase(username) && !"swissuser".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        // Secure password validation for MVP profiles
        String expectedPassword = "swissadmin".equalsIgnoreCase(username) ? "adminpassword" : "userpassword";
        if (password == null || !expectedPassword.equals(password)) {
            throw new IllegalArgumentException("Invalid username or password.");
        }

        boolean mfaRequired = true;
        
        if (mfaRequired) {
            String sessionToken = UUID.randomUUID().toString();
            String otpCode = String.format("%06d", random.nextInt(1000000));
            Instant expiry = Instant.now().plusSeconds(mfaOtpExpirationSec);

            mfaSessions.put(sessionToken, new MfaSession(username, otpCode, expiry));

            boolean isProduction = java.util.Arrays.asList(env.getActiveProfiles()).contains("prod") 
                                || java.util.Arrays.asList(env.getActiveProfiles()).contains("production");
            if (!isProduction) {
                System.out.println(String.format(
                    "\n[MFA GATEWAY] SMS OTP Broadcast to user %s. PIN code: %s (Expires in %ds)\n",
                    username, otpCode, mfaOtpExpirationSec
                ));
                System.out.flush();
                log.info("[MFA GATEWAY] SMS OTP Broadcast to user {}. PIN code: {} (Expires in {}s)", username, otpCode, mfaOtpExpirationSec);
            } else {
                log.info("[MFA GATEWAY] SMS OTP Broadcast initiated for user {}.", username);
            }

            return new LoginResponse(true, sessionToken, null);
        } else {
            String token = generateJwtToken(username);
            return new LoginResponse(false, null, token);
        }
    }

    @Override
    public String verifyMfa(String sessionToken, String code) {
        MfaSession session = mfaSessions.get(sessionToken);
        if (session == null) {
            throw new IllegalArgumentException("MFA session expired or invalid.");
        }

        if (Instant.now().isAfter(session.expiryTime())) {
            mfaSessions.remove(sessionToken);
            throw new IllegalArgumentException("MFA passcode expired.");
        }

        if (!session.code().equals(code)) {
            throw new IllegalArgumentException("Invalid MFA passcode verification.");
        }

        mfaSessions.remove(sessionToken);
        return generateJwtToken(session.username());
    }

    private String generateJwtToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String role = "swissadmin".equalsIgnoreCase(username) ? "ADMIN" : "CUSTOMER";
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }
}
