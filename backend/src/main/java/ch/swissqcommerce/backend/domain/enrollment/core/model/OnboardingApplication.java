package ch.swissqcommerce.backend.domain.enrollment.core.model;
import java.time.OffsetDateTime;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingApplication {

    private String applicationId;

    private String applicantType;

    private String name;

    private String details;

    private Boolean approvalOps = false;

    private Boolean approvalCompliance = false;

    private Boolean approvalAdmin = false;

    private OffsetDateTime createdAt;
}