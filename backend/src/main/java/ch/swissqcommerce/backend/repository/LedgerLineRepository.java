package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.LedgerLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerLineRepository extends JpaRepository<LedgerLineEntity, Integer> {
    List<LedgerLineEntity> findByAccountTypeAndActorIdOrderByLineIdDesc(String accountType, String actorId);
}

