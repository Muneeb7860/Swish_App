package ch.swissqcommerce.backend.domain.feedback.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
import ch.swissqcommerce.backend.domain.feedback.adapter.out.persistence.FeedbackEntity;
import ch.swissqcommerce.backend.domain.feedback.port.out.FeedbackOutPort;
import org.springframework.stereotype.Component;

@Component
public class FeedbackPersistenceAdapter implements FeedbackOutPort {

    private final FeedbackRepository repository;

    public FeedbackPersistenceAdapter(FeedbackRepository repository) {
        this.repository = repository;
    }

    @Override
    public Feedback saveFeedback(Feedback feedback) {
        if (feedback == null) return null;
        FeedbackEntity entity = FeedbackEntity.builder()
                .id(feedback.getId())
                .orderId(feedback.getOrderId())
                .riderRating(feedback.getRiderRating())
                .storeRating(feedback.getStoreRating())
                .productRating(feedback.getProductRating())
                .comments(feedback.getComments())
                .createdAt(feedback.getCreatedAt())
                .build();
        
        FeedbackEntity saved = repository.save(entity);
        
        return Feedback.builder()
                .id(saved.getId())
                .orderId(saved.getOrderId())
                .riderRating(saved.getRiderRating())
                .storeRating(saved.getStoreRating())
                .productRating(saved.getProductRating())
                .comments(saved.getComments())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}
