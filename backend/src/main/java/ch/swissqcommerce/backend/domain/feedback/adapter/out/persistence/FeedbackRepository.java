package ch.swissqcommerce.backend.domain.feedback.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.feedback.core.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {
}
