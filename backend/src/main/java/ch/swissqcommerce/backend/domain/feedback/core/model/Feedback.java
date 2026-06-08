package ch.swissqcommerce.backend.domain.feedback.core.model;

import lombok.*;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {
    private Long id;

    private Integer orderId;

    private Integer riderRating;

    private Integer storeRating;

    private Integer productRating;

    private String comments;

    private OffsetDateTime createdAt;
}
