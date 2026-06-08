package ch.swissqcommerce.backend.domain.dispatch.core.model;
import java.time.OffsetDateTime;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GearScan {

    private String scanId;

    private String riderId;

    private OffsetDateTime scanTime;

    private String gearType;

    private String verificationStatus;

    private String imageUrl;

    private String checkedBy;
}