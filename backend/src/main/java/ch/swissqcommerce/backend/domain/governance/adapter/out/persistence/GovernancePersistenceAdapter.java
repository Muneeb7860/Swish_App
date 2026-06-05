package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

@Component
public class GovernancePersistenceAdapter implements ProcurementApprovalPort {

    private final ProcurementApprovalRepository repository;

    public GovernancePersistenceAdapter(ProcurementApprovalRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ProcurementApproval> findById(Integer id) {
        return repository.findById(id);
    }

    @Override
    public ProcurementApproval save(ProcurementApproval approval) {
        return repository.save(approval);
    }

    @Override
    public List<ProcurementApproval> findAll() {
        return repository.findAll();
    }
}
