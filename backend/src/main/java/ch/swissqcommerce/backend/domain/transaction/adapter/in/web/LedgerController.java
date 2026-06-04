package ch.swissqcommerce.backend.domain.transaction.adapter.in.web;

import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    @Autowired
    private LedgerUseCase ledgerUseCase;

    @GetMapping
    public ResponseEntity<?> getCustomerLedger(@RequestParam String customerId) {
        return ResponseEntity.ok(ledgerUseCase.getCustomerLedger(customerId));
    }
}
