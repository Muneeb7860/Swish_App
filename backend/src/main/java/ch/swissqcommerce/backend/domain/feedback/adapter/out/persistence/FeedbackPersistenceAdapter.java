package ch.swissqcommerce.backend.domain.feedback.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
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
        return repository.save(feedback);
    }
}
