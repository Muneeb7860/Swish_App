package ch.swissqcommerce.backend.domain.feedback.adapter.in.web;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
import ch.swissqcommerce.backend.domain.feedback.port.in.FeedbackUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackUseCase feedbackUseCase;

    public FeedbackController(FeedbackUseCase feedbackUseCase) {
        this.feedbackUseCase = feedbackUseCase;
    }

    @PostMapping
    public ResponseEntity<Feedback> submitFeedback(@RequestBody Map<String, Object> payload) {
        Integer orderId = (Integer) payload.get("orderId");
        Integer rating = (Integer) payload.get("rating");
        String comments = (String) payload.get("comments");

        Feedback saved = feedbackUseCase.submitFeedback(orderId, rating, comments);
        return ResponseEntity.ok(saved);
    }
}
