package ch.swissqcommerce.backend.domain.event.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainEventRepository extends JpaRepository<DomainEventEntity, String> {}
