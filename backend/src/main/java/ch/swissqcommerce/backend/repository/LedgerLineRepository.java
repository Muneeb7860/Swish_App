package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.LedgerLineEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerLineRepository extends JpaRepository<LedgerLineEntity, Integer> {
    List<LedgerLineEntity> findByAccountTypeAndActorIdOrderByLineIdDesc(
            String accountType, String actorId);
}
