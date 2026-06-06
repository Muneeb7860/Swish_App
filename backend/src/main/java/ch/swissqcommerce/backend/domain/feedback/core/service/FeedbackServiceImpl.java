package ch.swissqcommerce.backend.domain.feedback.core.service;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
import ch.swissqcommerce.backend.domain.feedback.port.in.FeedbackUseCase;
import ch.swissqcommerce.backend.domain.feedback.port.out.FeedbackOutPort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class FeedbackServiceImpl implements FeedbackUseCase {

    private final FeedbackOutPort feedbackOutPort;

    public FeedbackServiceImpl(FeedbackOutPort feedbackOutPort) {
        this.feedbackOutPort = feedbackOutPort;
    }

    @Override
    public Feedback submitFeedback(Integer orderId, Integer riderRating, Integer storeRating, Integer productRating, String comments) {
        Feedback feedback = Feedback.builder()
                .orderId(orderId)
                .riderRating(riderRating)
                .storeRating(storeRating)
                .productRating(productRating)
                .comments(comments)
                .createdAt(OffsetDateTime.now())
                .build();
        return feedbackOutPort.saveFeedback(feedback);
    }
}
