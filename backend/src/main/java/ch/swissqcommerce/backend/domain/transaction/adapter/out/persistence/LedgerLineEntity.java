package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;

import ch.swissqcommerce.backend.model.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "ledger_lines", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_id")
    private Integer lineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    @JsonIgnore
    private JournalEntry journalEntry;

    @Column(name = "account_type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String accountType;

    @Column(name = "actor_id", length = 50)
    @Size(max = 50)
    private String actorId;

    @Column(name = "debit", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", precision = 10, scale = 2, nullable = false)
    @NotNull
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;
}
