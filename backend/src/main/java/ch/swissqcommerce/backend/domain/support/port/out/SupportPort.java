package ch.swissqcommerce.backend.domain.support.port.out;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import java.util.Optional;

public interface SupportPort {
    SupportTicket save(SupportTicket ticket);
    Optional<SupportTicket> findById(String ticketId);
}
