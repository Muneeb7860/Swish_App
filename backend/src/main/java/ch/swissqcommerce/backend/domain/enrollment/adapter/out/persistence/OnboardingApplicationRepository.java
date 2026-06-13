package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OnboardingApplicationRepository
        extends JpaRepository<OnboardingApplicationEntity, String> {
    List<OnboardingApplicationEntity> findByApplicantType(String applicantType);

    List<OnboardingApplicationEntity> findByApprovalAdminFalse();

    long countByApprovalAdminFalse();
}
