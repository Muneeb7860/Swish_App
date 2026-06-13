package ch.swissqcommerce.backend.domain.feedback.adapter.in.web;

import ch.swissqcommerce.backend.domain.feedback.adapter.in.web.dto.FeedbackRequestDTO;
import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
import ch.swissqcommerce.backend.domain.feedback.port.in.FeedbackUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackUseCase feedbackUseCase;

    public FeedbackController(FeedbackUseCase feedbackUseCase) {
        this.feedbackUseCase = feedbackUseCase;
    }

    @PostMapping
    public ResponseEntity<Feedback> submitFeedback(@Valid @RequestBody FeedbackRequestDTO request) {
        Integer rating = request.getRating();
        Integer riderRating = request.getRiderRating() != null ? request.getRiderRating() : rating;
        Integer storeRating = request.getStoreRating() != null ? request.getStoreRating() : rating;
        Integer productRating =
                request.getProductRating() != null ? request.getProductRating() : rating;

        if (riderRating == null || storeRating == null || productRating == null) {
            throw new IllegalArgumentException(
                    "Feedback must provide either a global rating or individual ratings for rider,"
                            + " store, and product.");
        }

        Feedback saved =
                feedbackUseCase.submitFeedback(
                        request.getOrderId(),
                        riderRating,
                        storeRating,
                        productRating,
                        request.getComments());
        return ResponseEntity.ok(saved);
    }
}
