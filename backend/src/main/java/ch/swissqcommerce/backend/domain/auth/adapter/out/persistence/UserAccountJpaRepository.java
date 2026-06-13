package ch.swissqcommerce.backend.domain.auth.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, String> {
    Optional<UserAccountEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
