package ch.swissqcommerce.backend.domain.transaction.core.model;

import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerLine {

    private Integer lineId;

    private JournalEntry journalEntry;

    private String accountType;

    private String actorId;

    private BigDecimal debit = BigDecimal.ZERO;

    private BigDecimal credit = BigDecimal.ZERO;
}
