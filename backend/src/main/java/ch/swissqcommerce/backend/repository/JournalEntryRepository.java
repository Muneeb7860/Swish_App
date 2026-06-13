package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.JournalEntryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Integer> {
    Optional<JournalEntryEntity> findFirstByOrderByEntryIdDesc();
}
