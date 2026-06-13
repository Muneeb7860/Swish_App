package ch.swissqcommerce.backend.domain.support.port.in;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import java.util.Optional;

public interface SupportUseCase {
    SupportTicket createTicket(SupportTicket ticket);

    Optional<SupportTicket> getTicket(String ticketId);
}
