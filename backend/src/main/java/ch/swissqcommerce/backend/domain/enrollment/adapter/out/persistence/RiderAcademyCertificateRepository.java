package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiderAcademyCertificateRepository
        extends JpaRepository<RiderAcademyCertificateEntity, Integer> {}
