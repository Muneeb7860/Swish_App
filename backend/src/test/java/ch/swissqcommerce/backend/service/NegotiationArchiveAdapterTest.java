package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.agent.adapter.out.archive.MongoNegotiationArchiveAdapter;
import ch.swissqcommerce.backend.domain.agent.adapter.out.archive.NoOpNegotiationArchiveAdapter;
import ch.swissqcommerce.backend.domain.agent.core.model.NegotiationEvent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class NegotiationArchiveAdapterTest {

    private NegotiationEvent event() {
        return NegotiationEvent.builder()
                .eventId("evt-1")
                .wholesalerId("wh-1")
                .itemId("item-1")
                .proposedPrice(new BigDecimal("1.40"))
                .quantity(100)
                .approved(true)
                .occurredAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void noOpAdapter_isSafe() {
        assertDoesNotThrow(() -> new NoOpNegotiationArchiveAdapter().archive(event()));
    }

    @Test
    void mongoAdapter_savesToNegotiationEventsCollection() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        new MongoNegotiationArchiveAdapter(mongoTemplate).archive(event());
        verify(mongoTemplate).save(any(NegotiationEvent.class), eq("negotiation_events"));
    }
}
