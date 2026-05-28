package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.core.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Integer> {
    Optional<JournalEntry> findFirstByOrderByEntryIdDesc();
}

