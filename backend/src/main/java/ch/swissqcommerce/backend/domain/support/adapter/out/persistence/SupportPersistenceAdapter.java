package ch.swissqcommerce.backend.domain.support.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import ch.swissqcommerce.backend.domain.support.port.out.SupportPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SupportPersistenceAdapter implements SupportPort {
    private final SupportTicketRepository repository;

    @Override
    public SupportTicket save(SupportTicket ticket) {
        SupportTicketEntity entity =
                SupportTicketEntity.builder()
                        .ticketId(ticket.getTicketId())
                        .customerId(ticket.getCustomerId())
                        .orderId(ticket.getOrderId())
                        .priority(ticket.getPriority())
                        .status(ticket.getStatus())
                        .build();
        repository.save(entity);
        return ticket;
    }

    @Override
    public Optional<SupportTicket> findById(String ticketId) {
        return repository
                .findById(ticketId)
                .map(
                        e ->
                                SupportTicket.builder()
                                        .ticketId(e.getTicketId())
                                        .customerId(e.getCustomerId())
                                        .orderId(e.getOrderId())
                                        .priority(e.getPriority())
                                        .status(e.getStatus())
                                        .build());
    }
}
