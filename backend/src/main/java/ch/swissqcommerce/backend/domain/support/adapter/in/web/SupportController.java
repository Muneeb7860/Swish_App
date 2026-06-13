package ch.swissqcommerce.backend.domain.support.adapter.in.web;

import ch.swissqcommerce.backend.domain.support.core.model.SupportTicket;
import ch.swissqcommerce.backend.domain.support.port.in.SupportUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportUseCase supportUseCase;

    @GetMapping("/tickets/{id}")
    public ResponseEntity<SupportTicket> getTicket(@PathVariable String id) {
        return supportUseCase
                .getTicket(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/tickets")
    public ResponseEntity<SupportTicket> createTicket(@RequestBody SupportTicket ticket) {
        return ResponseEntity.ok(supportUseCase.createTicket(ticket));
    }
}
