package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalEntity;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalEntityPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

@Component
public class GovernancePersistenceAdapter implements ProcurementApprovalEntityPort {

    private final ProcurementApprovalEntityRepository repository;

    public GovernancePersistenceAdapter(ProcurementApprovalEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ProcurementApprovalEntity> findById(Integer id) {
        return repository.findById(id);
    }

    @Override
    public ProcurementApprovalEntity save(ProcurementApprovalEntity approval) {
        return repository.save(approval);
    }

    @Override
    public List<ProcurementApprovalEntity> findAll() {
        return repository.findAll();
    }
}
