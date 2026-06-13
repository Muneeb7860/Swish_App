package ch.swissqcommerce.backend.domain.agent.adapter.out.archive;

import ch.swissqcommerce.backend.domain.agent.core.model.NegotiationEvent;
import ch.swissqcommerce.backend.domain.agent.port.out.NegotiationArchivePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * MongoDB CDC sink for negotiation events (BRD FR-02). Active only when {@code
 * swish.mongo.archive.enabled=true} (production), so dev/CI run without a MongoDB instance. Writes
 * are best-effort at the call site — an archive failure never blocks the negotiation.
 */
@Component
@ConditionalOnProperty(name = "swish.mongo.archive.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MongoNegotiationArchiveAdapter implements NegotiationArchivePort {

    private static final String COLLECTION = "negotiation_events";

    private final MongoTemplate mongoTemplate;

    @Override
    public void archive(NegotiationEvent event) {
        mongoTemplate.save(event, COLLECTION);
        log.info(
                "Negotiation archived to MongoDB: eventId={}, collection={}",
                event.getEventId(),
                COLLECTION);
    }
}
