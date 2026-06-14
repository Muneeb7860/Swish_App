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

    @Builder.Default private Boolean approvalOps = false;

    @Builder.Default private Boolean approvalCompliance = false;

    @Builder.Default private Boolean approvalAdmin = false;

    private OffsetDateTime createdAt;
}
