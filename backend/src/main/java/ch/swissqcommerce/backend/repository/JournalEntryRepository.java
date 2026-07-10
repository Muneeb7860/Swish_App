package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.JournalEntryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Integer> {
    Optional<JournalEntryEntity> findFirstByOrderByEntryIdDesc();

    /** All journal entries in append order — used to walk & verify the hash chain. */
    List<JournalEntryEntity> findAllByOrderByEntryIdAsc();
}
