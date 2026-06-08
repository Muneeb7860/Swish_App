package ch.swissqcommerce.backend.domain.transaction.core.model;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    private Integer entryId;

    private UUID entryUuid;

    private OffsetDateTime timestamp;

    private String reference;

    private String description;

    private String previousEntryHash;

    private String entryHash;

    private List<LedgerLine> ledgerLines;
}