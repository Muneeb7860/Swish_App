package ch.swissqcommerce.backend.domain.auth.port.out;

import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;
import java.util.Optional;

public interface UserRepositoryPort {
    UserAccount save(UserAccount userAccount);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(String id);
}
