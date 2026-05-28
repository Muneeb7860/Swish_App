package ch.swissqcommerce.backend.repository;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.transaction.core.model.LedgerLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerLineRepository extends JpaRepository<LedgerLine, Integer> {
    List<LedgerLine> findByAccountTypeAndActorIdOrderByLineIdDesc(String accountType, String actorId);
}

