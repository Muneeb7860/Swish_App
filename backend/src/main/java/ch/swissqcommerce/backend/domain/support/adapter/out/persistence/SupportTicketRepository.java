package ch.swissqcommerce.backend.domain.support.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, String> {
}
