package ch.swissqcommerce.backend.domain.reward.core.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardPoints {

    private String customerId;

    @Builder.Default private Integer loyaltyPoints = 0;
}
