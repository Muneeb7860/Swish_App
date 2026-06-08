package ch.swissqcommerce.backend.domain.fleet.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutLedger {
    private String ledgerId;
    private String riderId;
    private BigDecimal balance;
    private String status;

    public void addEarnings(BigDecimal amount) {
        if(balance == null) balance = BigDecimal.ZERO;
        this.balance = this.balance.add(amount);
    }
    public void processPayout() {
        this.balance = BigDecimal.ZERO;
        this.status = "PAID";
    }
}
