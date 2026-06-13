package ch.swissqcommerce.backend.domain.support.core.service;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import ch.swissqcommerce.backend.domain.support.port.in.SupportUseCase;
import ch.swissqcommerce.backend.domain.support.port.out.SupportPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupportServiceImpl implements SupportUseCase {
    private final SupportPort port;

    @Override
    public SupportTicket createTicket(SupportTicket ticket) {
        ticket.setStatus("OPEN");
        return port.save(ticket);
    }

    @Override
    public Optional<SupportTicket> getTicket(String ticketId) {
        return port.findById(ticketId);
    }
}
