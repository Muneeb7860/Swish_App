package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OnboardingApplicationRepository extends JpaRepository<OnboardingApplicationEntity, String> {
    List<OnboardingApplicationEntity> findByApplicantType(String applicantType);
    List<OnboardingApplicationEntity> findByApprovalAdminFalse();
    long countByApprovalAdminFalse();
}
