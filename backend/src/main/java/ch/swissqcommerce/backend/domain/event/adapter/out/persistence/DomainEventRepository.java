package ch.swissqcommerce.backend.domain.event.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.event.core.model.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {
}
