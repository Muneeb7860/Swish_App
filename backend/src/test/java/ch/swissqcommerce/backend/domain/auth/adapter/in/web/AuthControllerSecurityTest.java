package ch.swissqcommerce.backend.domain.auth.adapter.in.web;

import ch.swissqcommerce.backend.config.JwtAuthenticationFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuthControllerSecurityTest {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    public void testFilterExtractsAdminRole() throws Exception {
        // Generate admin token
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("swissadmin")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 100000))
                .signWith(key)
                .compact();

        // Clear security context
        SecurityContextHolder.clearContext();

        // Mock request and response
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Invoke filter
        ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "doFilterInternal", request, response, filterChain);

        // Assert authentication is set in security context and has ADMIN role
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("swissadmin", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));

        SecurityContextHolder.clearContext();
    }

    @Test
    public void testFilterExtractsCustomerRole() throws Exception {
        // Generate customer token
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("swissuser")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 100000))
                .signWith(key)
                .compact();

        // Clear security context
        SecurityContextHolder.clearContext();

        // Mock request and response
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Invoke filter
        ReflectionTestUtils.invokeMethod(jwtAuthenticationFilter, "doFilterInternal", request, response, filterChain);

        // Assert authentication is set in security context and has CUSTOMER role
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("swissuser", SecurityContextHolder.getContext().getAuthentication().getName());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority())));

        SecurityContextHolder.clearContext();
    }
}
