package ch.swissqcommerce.backend.domain.feedback.adapter.in.web.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackRequestDTO {

    @NotNull(message = "Order ID cannot be null")
    private Integer orderId;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot be more than 5")
    private Integer rating;

    @Min(value = 1, message = "Rider rating must be at least 1")
    @Max(value = 5, message = "Rider rating cannot be more than 5")
    private Integer riderRating;

    @Min(value = 1, message = "Store rating must be at least 1")
    @Max(value = 5, message = "Store rating cannot be more than 5")
    private Integer storeRating;

    @Min(value = 1, message = "Product rating must be at least 1")
    @Max(value = 5, message = "Product rating cannot be more than 5")
    private Integer productRating;

    @Size(max = 500, message = "Comments cannot exceed 500 characters")
    private String comments;
}
