package ch.swissqcommerce.backend.domain.agent.port.out;

import ch.swissqcommerce.backend.domain.agent.core.model.NegotiationEvent;

/** Outbound port for archiving negotiation events to the document store (FR-02). */
public interface NegotiationArchivePort {
    void archive(NegotiationEvent event);
}
