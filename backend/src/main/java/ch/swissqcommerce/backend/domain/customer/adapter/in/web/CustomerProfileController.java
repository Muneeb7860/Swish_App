package ch.swissqcommerce.backend.domain.customer.adapter.in.web;

import ch.swissqcommerce.backend.domain.customer.core.model.CustomerProfile;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerProfileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerProfileController {
    private final CustomerProfileUseCase profileUseCase;

    @GetMapping("/{id}")
    public ResponseEntity<CustomerProfile> getProfile(@PathVariable String id) {
        return profileUseCase.getProfile(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CustomerProfile> createProfile(@RequestBody CustomerProfile profile) {
        return ResponseEntity.ok(profileUseCase.createProfile(profile));
    }
}
