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

    @NotNull(message = "Rating cannot be null")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot be more than 5")
    private Integer rating;

    @Size(max = 500, message = "Comments cannot exceed 500 characters")
    private String comments;
}
