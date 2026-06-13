package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, String> {
    Optional<RiderEntity> findByFullName(String fullName);
}
