package ch.swissqcommerce.backend.domain.enrollment.core.model;

import java.time.OffsetDateTime;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiderAcademyCertificate {

    private Integer certificateId;

    private Rider rider;

    private String courseName;

    private OffsetDateTime completedAt;
}
