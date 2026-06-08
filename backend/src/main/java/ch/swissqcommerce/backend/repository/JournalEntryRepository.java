package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Integer> {
    Optional<JournalEntryEntity> findFirstByOrderByEntryIdDesc();
}

