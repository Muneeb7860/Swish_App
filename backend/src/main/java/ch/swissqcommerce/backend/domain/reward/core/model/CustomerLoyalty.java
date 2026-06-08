package ch.swissqcommerce.backend.domain.reward.core.model;
import java.time.OffsetDateTime;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerLoyalty {

    private Integer loyaltyId;

    private String customerId;

    private Integer pointsChanged;

    private String description;

    private OffsetDateTime createdAt;
}