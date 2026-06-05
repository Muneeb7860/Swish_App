package ch.swissqcommerce.backend.domain.governance.core.service;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalRepository;
import ch.swissqcommerce.backend.model.B2BRestockOrder;
import ch.swissqcommerce.backend.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.repository.B2BRestockOrderRepository;
import ch.swissqcommerce.backend.repository.OrderTelemetryLogRepository;
import ch.swissqcommerce.backend.exception.ResourceNotFoundException;
import ch.swissqcommerce.backend.exception.RuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Service
@Transactional
public class GovernanceServiceImpl implements GovernanceUseCase {

    private static final Logger log = LoggerFactory.getLogger(GovernanceServiceImpl.class);

    private final ProcurementApprovalRepository approvalsRepository;
    private final B2BRestockOrderRepository restockOrderRepository;
    private final OrderTelemetryLogRepository telemetryLogRepository;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public GovernanceServiceImpl(ProcurementApprovalRepository approvalsRepository,
                                 B2BRestockOrderRepository restockOrderRepository,
                                 OrderTelemetryLogRepository telemetryLogRepository) {
        this.approvalsRepository = approvalsRepository;
        this.restockOrderRepository = restockOrderRepository;
        this.telemetryLogRepository = telemetryLogRepository;
    }

    @PostConstruct
    public void initKeyPair() {
        try {
            java.io.File keyDir = new java.io.File("config/keys");
            if (!keyDir.exists()) {
                keyDir.mkdirs();
            }
            java.io.File privKeyFile = new java.io.File(keyDir, "governance_private.key");
            java.io.File pubKeyFile = new java.io.File(keyDir, "governance_public.key");

            if (privKeyFile.exists() && pubKeyFile.exists()) {
                byte[] privKeyBytes = java.nio.file.Files.readAllBytes(privKeyFile.toPath());
                byte[] pubKeyBytes = java.nio.file.Files.readAllBytes(pubKeyFile.toPath());

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                
                PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privKeyBytes);
                this.privateKey = keyFactory.generatePrivate(privSpec);

                X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubKeyBytes);
                this.publicKey = keyFactory.generatePublic(pubSpec);

                log.info("GovernanceServiceImpl: Loaded persistent RSA KeyPair successfully from config/keys.");
            } else {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();

                java.nio.file.Files.write(privKeyFile.toPath(), this.privateKey.getEncoded());
                java.nio.file.Files.write(pubKeyFile.toPath(), this.publicKey.getEncoded());

                log.info("GovernanceServiceImpl: Generated and saved new persistent RSA KeyPair in config/keys.");
            }
        } catch (Exception e) {
            log.error("GovernanceServiceImpl: Failed to initialize/load RSA KeyPair, falling back to in-memory key generation", e);
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair pair = keyGen.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
                log.warn("GovernanceServiceImpl: Fell back to in-memory transient RSA KeyPair.");
            } catch (Exception ex) {
                log.error("GovernanceServiceImpl: Fallback in-memory KeyPair generation failed", ex);
            }
        }
    }

    @Override
    public void auditNegotiation(Integer restockOrderId, String wholesalerId, BigDecimal amount) {
        B2BRestockOrder restockOrder = restockOrderRepository.findById(restockOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Restock order not found"));

        ProcurementApproval approval = ProcurementApproval.builder()
                .restockOrderId(restockOrderId)
                .wholesalerId(wholesalerId)
                .amount(amount)
                .status("PENDING")
                .build();
        approvalsRepository.save(approval);
        log.info("GovernanceServiceImpl: Created pending override request for restock order id={}, amount={}", 
                restockOrderId, amount);
    }

    @Override
    public void approveOverride(Integer approvalId, String operator, String reason) {
        ProcurementApproval approval = approvalsRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval ticket not found"));

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new RuleViolationException("Ticket is already " + approval.getStatus());
        }

        approval.setStatus("APPROVED");
        approval.setOverrideBy(operator);
        approval.setOverrideReason(reason);
        approvalsRepository.save(approval);

        if (approval.getRestockOrderId() != null) {
            restockOrderRepository.findById(approval.getRestockOrderId()).ifPresent(order -> {
                order.setStatus("fulfilled");
                restockOrderRepository.save(order);
                log.info("GovernanceServiceImpl: B2B Restock order id={} approved by operator={}.", 
                        order.getRestockOrderId(), operator);
            });
        }
    }

    @Override
    public void rejectOverride(Integer approvalId, String operator, String reason) {
        ProcurementApproval approval = approvalsRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval ticket not found"));

        if (!"PENDING".equalsIgnoreCase(approval.getStatus())) {
            throw new RuleViolationException("Ticket is already " + approval.getStatus());
        }

        approval.setStatus("REJECTED");
        approval.setOverrideBy(operator);
        approval.setOverrideReason(reason);
        approvalsRepository.save(approval);

        if (approval.getRestockOrderId() != null) {
            restockOrderRepository.findById(approval.getRestockOrderId()).ifPresent(order -> {
                order.setStatus("failed");
                restockOrderRepository.save(order);
                log.warn("GovernanceServiceImpl: B2B Restock order id={} rejected by operator={}.", 
                        order.getRestockOrderId(), operator);
            });
        }
    }

    @Override
    public String signDeliverySummary(String orderId, String podHash) {
        log.info("GovernanceServiceImpl: Generating digital signature for order id={}, podHash={}", orderId, podHash);
        try {
            int orderIdInt = Integer.parseInt(orderId);
            List<OrderTelemetryLog> logs = telemetryLogRepository.findByOrderOrderIdOrderByDeviceTimestampDesc(orderIdInt);
            if (logs.isEmpty()) {
                throw new ResourceNotFoundException("No telemetry logs found for order " + orderId);
            }

            BigDecimal minTemp = BigDecimal.valueOf(999.0);
            BigDecimal maxTemp = BigDecimal.valueOf(-999.0);
            BigDecimal sumTemp = BigDecimal.ZERO;

            for (OrderTelemetryLog tLog : logs) {
                BigDecimal temp = tLog.getTemperature();
                if (temp.compareTo(minTemp) < 0) minTemp = temp;
                if (temp.compareTo(maxTemp) > 0) maxTemp = temp;
                sumTemp = sumTemp.add(temp);
            }

            BigDecimal avgTemp = sumTemp.divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);
            String summaryPayload = String.format("orderId:%s|minTemp:%s|maxTemp:%s|avgTemp:%s|podHash:%s", 
                    orderId, minTemp, maxTemp, avgTemp, podHash);

            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(privateKey);
            privateSignature.update(summaryPayload.getBytes(StandardCharsets.UTF_8));
            byte[] signature = privateSignature.sign();

            String encodedSignature = Base64.getEncoder().encodeToString(signature);
            log.info("GovernanceServiceImpl: Cryptographic signature generated: {}", encodedSignature);
            return encodedSignature;
        } catch (Exception e) {
            log.error("GovernanceServiceImpl: Failed to sign telemetry summary for order {}", orderId, e);
            throw new RuntimeException("Cryptographic signing failed", e);
        }
    }
}
