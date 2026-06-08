package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, String> {
    Optional<RiderEntity> findByFullName(String fullName);
}
