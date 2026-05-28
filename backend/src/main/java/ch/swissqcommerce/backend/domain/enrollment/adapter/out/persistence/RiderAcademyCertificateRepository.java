package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.core.model.RiderAcademyCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderAcademyCertificateRepository extends JpaRepository<RiderAcademyCertificate, Integer> {
}
