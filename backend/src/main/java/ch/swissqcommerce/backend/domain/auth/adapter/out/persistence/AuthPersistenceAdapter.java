package ch.swissqcommerce.backend.domain.auth.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import ch.swissqcommerce.backend.domain.auth.core.model.Session;
import ch.swissqcommerce.backend.domain.auth.port.out.UserRepositoryPort;
import ch.swissqcommerce.backend.domain.auth.port.out.SessionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthPersistenceAdapter implements UserRepositoryPort {

    @Override
    public UserAccount save(UserAccount userAccount) {
        return userAccount;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return Optional.empty();
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        return Optional.empty();
    }
}
