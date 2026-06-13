package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.auth.core.model.*;
import ch.swissqcommerce.backend.domain.auth.core.service.AuthServiceImpl;
import ch.swissqcommerce.backend.domain.auth.port.out.AuthEventPublisherPort;
import ch.swissqcommerce.backend.domain.auth.port.out.SessionRepositoryPort;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock private UserRepositoryPort userRepositoryPort;

    @Mock private SessionRepositoryPort sessionRepositoryPort;

    @Mock private AuthEventPublisherPort eventPublisherPort;

    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthServiceImpl authService;

    private UserAccount mockUser;

    @BeforeEach
    public void setUp() {
        mockUser =
                UserAccount.builder()
                        .id("user-123")
                        .emailAddress(new EmailAddress("test@example.com"))
                        .passwordHash(new PasswordHash("hashedPassword"))
                        .status(AccountStatus.ACTIVE)
                        .build();
    }

    @Test
    public void testLogin_Success() {
        when(userRepositoryPort.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(sessionRepositoryPort.save(any(Session.class))).thenAnswer(i -> i.getArguments()[0]);

        Session session =
                authService.login("test@example.com", "password123", "fingerprint", "127.0.0.1");

        assertNotNull(session);
        assertEquals("user-123", session.getUserId());
        assertTrue(session.isActive());
        verify(sessionRepositoryPort).save(any(Session.class));
    }

    @Test
    public void testLogin_InvalidEmail() {
        when(userRepositoryPort.findByEmail("wrong@example.com")).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    authService.login("wrong@example.com", "password", "fingerprint", "127.0.0.1");
                });

        verify(passwordEncoder).matches(eq("password"), anyString());
    }

    @Test
    public void testLogin_InvalidPassword() {
        when(userRepositoryPort.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrongpass", "hashedPassword")).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    authService.login("test@example.com", "wrongpass", "fingerprint", "127.0.0.1");
                });
    }

    @Test
    public void testLogin_LockedAccount() {
        mockUser.setStatus(AccountStatus.LOCKED);
        when(userRepositoryPort.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    authService.login(
                            "test@example.com", "password123", "fingerprint", "127.0.0.1");
                });
    }

    @Test
    public void testLogout() {
        Session session = Session.builder().id("session-1").active(true).build();
        when(sessionRepositoryPort.findById("session-1")).thenReturn(Optional.of(session));

        authService.logout("session-1");

        assertFalse(session.isActive());
        verify(sessionRepositoryPort).save(session);
    }

    @Test
    public void testValidateSession_Valid() {
        Session session =
                Session.builder()
                        .id("session-1")
                        .active(true)
                        .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                        .build();
        when(sessionRepositoryPort.findById("session-1")).thenReturn(Optional.of(session));

        boolean isValid = authService.validateSession("session-1");
        assertTrue(isValid);
    }

    @Test
    public void testValidateSession_Invalid() {
        when(sessionRepositoryPort.findById("session-2")).thenReturn(Optional.empty());

        boolean isValid = authService.validateSession("session-2");
        assertFalse(isValid);
    }

    @Test
    public void testRegister_Success() {
        when(userRepositoryPort.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");
        when(userRepositoryPort.save(any(UserAccount.class)))
                .thenAnswer(
                        i -> {
                            UserAccount account = i.getArgument(0);
                            return account;
                        });

        UserAccount newUser = authService.register("new@example.com", "password123");

        assertNotNull(newUser);
        assertEquals("new@example.com", newUser.getEmailAddress().getValue());
        verify(eventPublisherPort).publishUserRegisteredEvent(anyString(), eq("new@example.com"));
    }

    @Test
    public void testRegister_EmailAlreadyInUse() {
        when(userRepositoryPort.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    authService.register("test@example.com", "password123");
                });
    }
}
