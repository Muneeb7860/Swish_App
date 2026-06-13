package ch.swissqcommerce.backend.domain.retailer.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;
import ch.swissqcommerce.backend.domain.retailer.port.out.RetailerPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetailerPersistenceAdapter implements RetailerPort {

    private final RetailerRepository repository;

    @Override
    public Retailer save(Retailer retailer) {
        RetailerEntity entity =
                RetailerEntity.builder()
                        .retailerId(retailer.getRetailerId())
                        .name(retailer.getName())
                        .contactEmail(retailer.getContactEmail())
                        .storeId(retailer.getStoreId())
                        .tier(retailer.getTier().name())
                        .status(retailer.getStatus())
                        .approvalOps(retailer.isApprovalOps())
                        .approvalCompliance(retailer.isApprovalCompliance())
                        .approvalAdmin(retailer.isApprovalAdmin())
                        .apiKeyHash(retailer.getApiKeyHash())
                        .billingAccountId(retailer.getBillingAccountId())
                        .build();
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Retailer> findById(String retailerId) {
        return repository.findById(retailerId).map(this::toDomain);
    }

    @Override
    public Optional<Retailer> findByApiKeyHash(String apiKeyHash) {
        return repository.findByApiKeyHash(apiKeyHash).map(this::toDomain);
    }

    private Retailer toDomain(RetailerEntity e) {
        return Retailer.builder()
                .retailerId(e.getRetailerId())
                .name(e.getName())
                .contactEmail(e.getContactEmail())
                .storeId(e.getStoreId())
                .tier(BillingTier.valueOf(e.getTier()))
                .status(e.getStatus())
                .approvalOps(e.isApprovalOps())
                .approvalCompliance(e.isApprovalCompliance())
                .approvalAdmin(e.isApprovalAdmin())
                .apiKeyHash(e.getApiKeyHash())
                .billingAccountId(e.getBillingAccountId())
                .build();
    }
}
