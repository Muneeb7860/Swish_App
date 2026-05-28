package ch.swissqcommerce.backend.domain.enrollment.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "onboarding_applications", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingApplication {

    @Id
    @Column(name = "application_id", length = 50)
    @Size(max = 50)
    private String applicationId;

    @Column(name = "applicant_type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String applicantType;

    @Column(name = "name", length = 100, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String name;

    @Column(name = "details", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String details;

    @Column(name = "approval_ops", nullable = false)
    @Builder.Default
    private Boolean approvalOps = false;

    @Column(name = "approval_compliance", nullable = false)
    @Builder.Default
    private Boolean approvalCompliance = false;

    @Column(name = "approval_admin", nullable = false)
    @Builder.Default
    private Boolean approvalAdmin = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
