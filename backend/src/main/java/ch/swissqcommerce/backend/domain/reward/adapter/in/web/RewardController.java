package ch.swissqcommerce.backend.domain.reward.adapter.in.web;

import ch.swissqcommerce.backend.domain.reward.port.in.RewardUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardUseCase rewardUseCase;

    public RewardController(RewardUseCase rewardUseCase) {
        this.rewardUseCase = rewardUseCase;
    }

    @PostMapping("/{customerId}/add")
    public ResponseEntity<Void> addPoints(
            @PathVariable String customerId, @RequestParam int amount) {
        rewardUseCase.addPoints(customerId, amount);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{customerId}/redeem")
    public ResponseEntity<Void> redeemPoints(
            @PathVariable String customerId, @RequestParam int amount) {
        rewardUseCase.redeemPoints(customerId, amount);
        return ResponseEntity.ok().build();
    }
}
