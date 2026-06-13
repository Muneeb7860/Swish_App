package ch.swissqcommerce.backend.domain.retailer.core.service;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.port.in.BillingUseCase;
import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;
import ch.swissqcommerce.backend.domain.retailer.port.in.RetailerUseCase;
import ch.swissqcommerce.backend.domain.retailer.port.out.RetailerPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetailerServiceImpl implements RetailerUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RetailerPort port;
    private final BillingUseCase billing;

    @Override
    @Transactional
    public Retailer register(String name, String contactEmail, String storeId, BillingTier tier) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (contactEmail == null || !contactEmail.contains("@"))
            throw new IllegalArgumentException("valid contactEmail is required");
        if (storeId == null || storeId.isBlank())
            throw new IllegalArgumentException("storeId (hub) is required");
        if (tier == null) throw new IllegalArgumentException("tier is required");

        Retailer retailer =
                Retailer.builder()
                        .retailerId(
                                "RTL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .name(name)
                        .contactEmail(contactEmail)
                        .storeId(storeId)
                        .tier(tier)
                        .status("PENDING")
                        .approvalOps(false)
                        .approvalCompliance(false)
                        .approvalAdmin(false)
                        .build();
        return port.save(retailer);
    }

    @Override
    @Transactional
    public ApprovalResult approveGate(String retailerId, String gate) {
        Retailer retailer =
                port.findById(retailerId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Retailer not found: " + retailerId));
        if ("REJECTED".equals(retailer.getStatus())) {
            throw new IllegalStateException("Retailer application was rejected");
        }
        if (gate == null) throw new IllegalArgumentException("gate is required");

        // Sequential 3-gate pattern: ops -> compliance -> admin.
        switch (gate.toLowerCase()) {
            case "ops" -> retailer.setApprovalOps(true);
            case "compliance" -> {
                if (!retailer.isApprovalOps())
                    throw new IllegalStateException("Ops approval must precede compliance");
                retailer.setApprovalCompliance(true);
            }
            case "admin" -> {
                if (!retailer.isApprovalOps() || !retailer.isApprovalCompliance())
                    throw new IllegalStateException(
                            "Ops and compliance approvals required before admin");
                retailer.setApprovalAdmin(true);
            }
            default ->
                    throw new IllegalArgumentException(
                            "Invalid gate: " + gate + " (ops|compliance|admin)");
        }

        boolean fullyApproved =
                retailer.isApprovalOps()
                        && retailer.isApprovalCompliance()
                        && retailer.isApprovalAdmin();
        String issuedApiKey = null;

        if (fullyApproved && "PENDING".equals(retailer.getStatus())) {
            // Activation: issue API key (store only the hash), create a billing account.
            issuedApiKey = generateApiKey();
            retailer.setApiKeyHash(sha256(issuedApiKey));
            BillingAccount account = billing.subscribe(retailer.getStoreId(), retailer.getTier());
            retailer.setBillingAccountId(account.getAccountId());
            retailer.setStatus("ACTIVE");
        }

        Retailer saved = port.save(retailer);
        return new ApprovalResult(saved, issuedApiKey);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Retailer> getRetailer(String retailerId) {
        return port.findById(retailerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Retailer> authenticateByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        return port.findByApiKeyHash(sha256(apiKey)).filter(r -> "ACTIVE".equals(r.getStatus()));
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "rtl_" + HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
