package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.enrollment.core.model.OnboardingApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OnboardingApplicationRepository extends JpaRepository<OnboardingApplication, String> {
    List<OnboardingApplication> findByApplicantType(String applicantType);
    List<OnboardingApplication> findByApprovalAdminFalse();
}
