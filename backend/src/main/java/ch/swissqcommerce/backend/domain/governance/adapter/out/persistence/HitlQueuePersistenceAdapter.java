package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.governance.port.out.HitlQueuePort;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adapter backing {@link HitlQueuePort} with the existing {@link HitlQueueRepository}
 * (the same table {@code MasterOrchestratorService} writes agent escalations to).
 */
@Component
public class HitlQueuePersistenceAdapter implements HitlQueuePort {

    private final HitlQueueRepository repository;

    public HitlQueuePersistenceAdapter(HitlQueueRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<HitlQueue> findByStatus(String status) {
        return repository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public Optional<HitlQueue> findByTicketId(String ticketId) {
        return repository.findById(ticketId);
    }

    @Override
    public HitlQueue save(HitlQueue ticket) {
        return repository.save(ticket);
    }

    @Override
    public long countByStatus(String status) {
        return repository.countByStatus(status);
    }
}
