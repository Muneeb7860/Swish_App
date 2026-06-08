package ch.swissqcommerce.backend.domain.feedback.core.model;
import java.time.OffsetDateTime;


import lombok.*;

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