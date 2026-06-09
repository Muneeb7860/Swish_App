package ch.swissqcommerce.backend.config;

import ch.swissqcommerce.backend.domain.auth.adapter.out.persistence.UserAccountEntity;
import ch.swissqcommerce.backend.domain.auth.adapter.out.persistence.UserAccountJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds a single ADMIN user on application startup when both {@code ADMIN_EMAIL}
 * and {@code ADMIN_PASSWORD} environment variables are present.
 *
 * <p>Runs only on the {@code default} and {@code dev} Spring profiles so it
 * never executes in production (profile {@code prod}).
 *
 * <p>Idempotent: if the email already exists the record is <em>not</em> updated
 * — restart-safe and safe to run in CI on every boot.
 *
 * <p>Cypress CI usage:
 * <pre>
 *   ADMIN_EMAIL=ci-admin@swish.local
 *   ADMIN_PASSWORD=CiAdm1nP@ss!
 * </pre>
 * After the backend is UP, Cypress (or the CI step) calls
 * {@code POST /api/v1/auth/login} with those credentials to obtain an ADMIN JWT
 * and stores it as {@code CYPRESS_ADMIN_TOKEN}.
 */
@Component
@Profile({"default", "dev"})
@RequiredArgsConstructor
@Slf4j
public class DevAdminInitializer implements ApplicationRunner {

    private final UserAccountJpaRepository userAccountJpaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String adminEmail    = System.getenv("ADMIN_EMAIL");
        String adminPassword = System.getenv("ADMIN_PASSWORD");

        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.debug("DevAdminInitializer: ADMIN_EMAIL / ADMIN_PASSWORD not set — skipping admin seed.");
            return;
        }

        if (userAccountJpaRepository.existsByEmail(adminEmail)) {
            log.info("DevAdminInitializer: admin account {} already exists — no action taken.", adminEmail);
            return;
        }

        UserAccountEntity adminEntity = UserAccountEntity.builder()
                .userId(UUID.randomUUID().toString())
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .status("ACTIVE")
                .role("ADMIN")
                .build();

        userAccountJpaRepository.save(adminEntity);
        log.info("DevAdminInitializer: seeded ADMIN account {}.", adminEmail);
    }
}
