package ch.swissqcommerce.backend.domain.feedback.port.out;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;

public interface FeedbackOutPort {
    Feedback saveFeedback(Feedback feedback);
}
