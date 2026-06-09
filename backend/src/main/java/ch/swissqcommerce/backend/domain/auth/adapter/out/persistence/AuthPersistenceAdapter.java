package ch.swissqcommerce.backend.domain.auth.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.auth.core.model.AccountStatus;
import ch.swissqcommerce.backend.domain.auth.core.model.EmailAddress;
import ch.swissqcommerce.backend.domain.auth.core.model.PasswordHash;
import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthPersistenceAdapter implements UserRepositoryPort {

    private final UserAccountJpaRepository jpa;

    @Override
    public UserAccount save(UserAccount userAccount) {
        UserAccountEntity entity = toEntity(userAccount);
        UserAccountEntity saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return jpa.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    private UserAccountEntity toEntity(UserAccount user) {
        return UserAccountEntity.builder()
                .userId(user.getId())
                .email(user.getEmailAddress().getValue())
                .passwordHash(user.getPasswordHash().getValue())
                .status(user.getStatus() != null ? user.getStatus().name() : AccountStatus.ACTIVE.name())
                .role(user.getRole() != null ? user.getRole() : "CUSTOMER")
                .build();
    }

    private UserAccount toDomain(UserAccountEntity e) {
        return UserAccount.builder()
                .id(e.getUserId())
                .emailAddress(new EmailAddress(e.getEmail()))
                .passwordHash(new PasswordHash(e.getPasswordHash()))
                .status(AccountStatus.valueOf(e.getStatus()))
                .role(e.getRole() != null ? e.getRole() : "CUSTOMER")
                .build();
    }
}
