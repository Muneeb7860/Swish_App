package ch.swissqcommerce.backend.domain.governance.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.out.ProcurementApprovalPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GovernancePersistenceAdapter implements ProcurementApprovalPort {

    private final ProcurementApprovalRepository repository;

    public GovernancePersistenceAdapter(ProcurementApprovalRepository repository) {
        this.repository = repository;
    }

    private ProcurementApproval mapToDomain(ProcurementApprovalEntity entity) {
        if (entity == null) return null;
        return ProcurementApproval.builder()
                .id(entity.getId())
                .restockOrderId(entity.getRestockOrderId())
                .wholesalerId(entity.getWholesalerId())
                .amount(entity.getAmount())
                .status(entity.getStatus())
                .overrideBy(entity.getOverrideBy())
                .overrideReason(entity.getOverrideReason())
                .build();
    }

    private ProcurementApprovalEntity mapToEntity(ProcurementApproval domain) {
        if (domain == null) return null;
        return ProcurementApprovalEntity.builder()
                .id(domain.getId())
                .restockOrderId(domain.getRestockOrderId())
                .wholesalerId(domain.getWholesalerId())
                .amount(domain.getAmount())
                .status(domain.getStatus())
                .overrideBy(domain.getOverrideBy())
                .overrideReason(domain.getOverrideReason())
                .build();
    }

    @Override
    public Optional<ProcurementApproval> findById(Integer id) {
        return repository.findById(id).map(this::mapToDomain);
    }

    @Override
    public ProcurementApproval save(ProcurementApproval approval) {
        ProcurementApprovalEntity entity = mapToEntity(approval);
        ProcurementApprovalEntity saved = repository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public List<ProcurementApproval> findAll() {
        return repository.findAll().stream().map(this::mapToDomain).toList();
    }
}
