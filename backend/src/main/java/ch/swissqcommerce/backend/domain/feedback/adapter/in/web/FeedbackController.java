package ch.swissqcommerce.backend.domain.feedback.adapter.in.web;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
import ch.swissqcommerce.backend.domain.feedback.port.in.FeedbackUseCase;
import ch.swissqcommerce.backend.domain.feedback.adapter.in.web.dto.FeedbackRequestDTO;
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
        Feedback saved = feedbackUseCase.submitFeedback(request.getOrderId(), request.getRating(), request.getComments());
        return ResponseEntity.ok(saved);
    }
}
