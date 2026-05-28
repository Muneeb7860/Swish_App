package ch.swissqcommerce.backend.domain.enrollment.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rider_academy_certificates", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiderAcademyCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certificate_id")
    private Integer certificateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id", nullable = false)
    private Rider rider;

    @NotBlank
    @Column(name = "course_name", length = 100, nullable = false)
    private String courseName;

    @Column(name = "completed_at", insertable = false, updatable = false)
    private OffsetDateTime completedAt;
}
