package ch.swissqcommerce.backend.domain.agent.adapter.out.archive;

import ch.swissqcommerce.backend.domain.agent.core.model.NegotiationEvent;
import ch.swissqcommerce.backend.domain.agent.port.out.NegotiationArchivePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default negotiation archive — active when MongoDB archiving is disabled (dev / CI). Keeps the app
 * fully functional and the pipeline green without a MongoDB instance; the Mongo adapter takes over
 * when {@code swish.mongo.archive.enabled=true}.
 */
@Component
@ConditionalOnProperty(
        name = "swish.mongo.archive.enabled",
        havingValue = "false",
        matchIfMissing = true)
@Slf4j
public class NoOpNegotiationArchiveAdapter implements NegotiationArchivePort {

    @Override
    public void archive(NegotiationEvent event) {
        log.debug(
                "Negotiation archive (no-op; Mongo disabled): eventId={}, wholesaler={},"
                        + " approved={}",
                event.getEventId(),
                event.getWholesalerId(),
                event.isApproved());
    }
}
