package ch.swissqcommerce.backend.domain.dispatch.adapter.out.persistence;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gear_scans", schema = "dispatch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GearScanEntity {

    @Id
    @Column(name = "scan_id", length = 50)
    @NotBlank
    @Size(max = 50)
    private String scanId;

    @Column(name = "rider_id", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String riderId;

    @Column(name = "scan_time", insertable = false, updatable = false)
    private OffsetDateTime scanTime;

    @Column(name = "gear_type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String gearType;

    @Column(name = "verification_status", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String verificationStatus;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "checked_by", length = 50)
    @Size(max = 50)
    private String checkedBy;
}