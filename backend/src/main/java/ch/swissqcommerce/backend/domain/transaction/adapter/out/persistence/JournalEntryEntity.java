package ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence;

import ch.swissqcommerce.backend.model.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Integer entryId;

    @Column(name = "entry_uuid", unique = true, nullable = false)
    @NotNull
    private UUID entryUuid;

    @Column(name = "timestamp", insertable = false, updatable = false)
    private OffsetDateTime timestamp;

    @Column(name = "reference", length = 50, nullable = false)
    @NotBlank
    @Size(max = 50)
    private String reference;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String description;

    @Column(name = "previous_entry_hash", length = 64)
    private String previousEntryHash;

    @Column(name = "entry_hash", length = 64)
    private String entryHash;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerLineEntity> ledgerLines;
}