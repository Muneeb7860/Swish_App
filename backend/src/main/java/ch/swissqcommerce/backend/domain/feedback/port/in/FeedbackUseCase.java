package ch.swissqcommerce.backend.domain.feedback.port.in;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;

public interface FeedbackUseCase {
    Feedback submitFeedback(Integer orderId, Integer rating, String comments);
}
