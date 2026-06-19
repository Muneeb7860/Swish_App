package ch.swissqcommerce.backend.domain.governance.adapter.in.web;

import ch.swissqcommerce.backend.domain.governance.core.model.AssigneeRole;
import ch.swissqcommerce.backend.gateway.ExecutionGateway;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.PolicyDecision;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.PolicyDecisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hitl/tasks")
@CrossOrigin(origins = "*")
public class HitlTaskController {

    private static final Logger log = LoggerFactory.getLogger(HitlTaskController.class);

    private final AgentSuggestionEntityRepository agentSuggestionRepo;
    private final ExecutionGateway executionGateway;
    private final HitlQueueRepository hitlQueueRepo;
    private final PolicyDecisionRepository policyDecisionRepo;
    private final ObjectMapper objectMapper;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    public HitlTaskController(
            AgentSuggestionEntityRepository agentSuggestionRepo,
            ExecutionGateway executionGateway,
            HitlQueueRepository hitlQueueRepo,
            PolicyDecisionRepository policyDecisionRepo,
            ObjectMapper objectMapper,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.agentSuggestionRepo = agentSuggestionRepo;
        this.executionGateway = executionGateway;
        this.hitlQueueRepo = hitlQueueRepo;
        this.policyDecisionRepo = policyDecisionRepo;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRICING_MANAGER', 'OPS_MANAGER', 'LOGISTICS_MANAGER', 'RISK_ANALYST', 'SUPPORT_LEAD')")
    public ResponseEntity<Page<HitlTaskResponse>> listTasks(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(required = false, name = "assignee_role") String assigneeRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,asc") String sort
    ) {
        String domain = null;
        if (assigneeRole != null && !assigneeRole.trim().isEmpty()) {
            AssigneeRole role = AssigneeRole.fromString(assigneeRole);
            domain = role.getDomain();
        }

        String[] sortParts = sort.split(",");
        String sortProperty = sortParts[0];
        Sort.Direction sortDirection = 
            (sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])) 
                ? Sort.Direction.DESC 
                : Sort.Direction.ASC;
                
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortProperty));

        Page<AgentSuggestionEntity> entities;
        if (domain != null) {
            entities = agentSuggestionRepo.findByStatusAndDomain(status, domain, pageable);
        } else {
            entities = agentSuggestionRepo.findByStatus(status, pageable);
        }

        Page<HitlTaskResponse> dtoPage = entities.map(this::toResponse);
        return ResponseEntity.ok(dtoPage);
    }

    @PostMapping("/{suggestionId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRICING_MANAGER', 'OPS_MANAGER', 'LOGISTICS_MANAGER', 'RISK_ANALYST', 'SUPPORT_LEAD')")
    @Transactional
    public ResponseEntity<?> approve(
            @PathVariable UUID suggestionId,
            @RequestBody TaskOverrideRequest request
    ) {
        AgentSuggestionEntity suggestion = agentSuggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                        "Suggestion not found: " + suggestionId));

        checkPermission(suggestion.getDomain());

        // Idempotency check
        if ("approved".equalsIgnoreCase(suggestion.getStatus()) || "executed".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.ok(toResponse(suggestion));
        }
        
        if ("rejected".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", "Task already rejected"));
        }
        
        if ("failed".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", "Task already failed"));
        }

        // Expiry check
        if (OffsetDateTime.now().isAfter(suggestion.getExpiresAt())) {
            suggestion.setStatus("expired");
            agentSuggestionRepo.save(suggestion);
            if (meterRegistry != null) {
                var counter = meterRegistry.counter("agent_suggestions_total",
                        "domain", suggestion.getDomain(),
                        "decision", "expired",
                        "agent_name", suggestion.getAgent() != null ? suggestion.getAgent().getName() : "UnknownAgent"
                );
                if (counter != null) {
                    counter.increment();
                }
            }
            throw new IllegalStateException("Suggestion is expired");
        }

        if (!"pending".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", "Task not in pending status"));
        }

        // Approve suggestion state
        suggestion.setStatus("approved");
        agentSuggestionRepo.save(suggestion);

        PolicyDecision policyDecision = PolicyDecision.builder()
                .suggestion(suggestion)
                .decision("approved")
                .policyVersion("v1")
                .reason(request.getReason())
                .decidedBy("user:" + (request.getOperator() != null ? request.getOperator() : "anonymous"))
                .build();
        policyDecisionRepo.save(policyDecision);

        // Resolve associated HitlQueue ticket
        resolveHitlQueueTicket(suggestion, true, request.getOperator(), request.getReason());

        // Execute DB write
        executionGateway.execute(suggestionId, request.getOperator());

        // Reload suggestion state
        suggestion = agentSuggestionRepo.findById(suggestionId).orElseThrow();

        return ResponseEntity.ok(toResponse(suggestion));
    }

    @PostMapping("/{suggestionId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRICING_MANAGER', 'OPS_MANAGER', 'LOGISTICS_MANAGER', 'RISK_ANALYST', 'SUPPORT_LEAD')")
    @Transactional
    public ResponseEntity<?> reject(
            @PathVariable UUID suggestionId,
            @RequestBody TaskOverrideRequest request
    ) {
        AgentSuggestionEntity suggestion = agentSuggestionRepo.findById(suggestionId)
                .orElseThrow(() -> new ch.swissqcommerce.backend.exception.ResourceNotFoundException(
                        "Suggestion not found: " + suggestionId));

        checkPermission(suggestion.getDomain());

        // Idempotency check
        if ("rejected".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.ok(toResponse(suggestion));
        }

        if ("approved".equalsIgnoreCase(suggestion.getStatus()) || "executed".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", "Task already approved"));
        }

        if ("failed".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", "Task already failed"));
        }

        // Expiry check
        if (OffsetDateTime.now().isAfter(suggestion.getExpiresAt())) {
            suggestion.setStatus("expired");
            agentSuggestionRepo.save(suggestion);
            if (meterRegistry != null) {
                var counter = meterRegistry.counter("agent_suggestions_total",
                        "domain", suggestion.getDomain(),
                        "decision", "expired",
                        "agent_name", suggestion.getAgent() != null ? suggestion.getAgent().getName() : "UnknownAgent"
                );
                if (counter != null) {
                    counter.increment();
                }
            }
            throw new IllegalStateException("Suggestion is expired");
        }

        if (!"pending".equalsIgnoreCase(suggestion.getStatus())) {
            return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", "Task not in pending status"));
        }

        // Reject suggestion state
        suggestion.setStatus("rejected");
        agentSuggestionRepo.save(suggestion);

        PolicyDecision policyDecision = PolicyDecision.builder()
                .suggestion(suggestion)
                .decision("rejected")
                .policyVersion("v1")
                .reason(request.getReason())
                .decidedBy("user:" + (request.getOperator() != null ? request.getOperator() : "anonymous"))
                .build();
        policyDecisionRepo.save(policyDecision);

        // Resolve associated HitlQueue ticket
        resolveHitlQueueTicket(suggestion, false, request.getOperator(), request.getReason());

        return ResponseEntity.ok(toResponse(suggestion));
    }

    private void checkPermission(String domain) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;
        
        java.util.Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        boolean isAdmin = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;
        
        if ("pricing".equals(domain)) {
            boolean isPricingManager = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_PRICING_MANAGER"));
            if (!isPricingManager) {
                throw new AccessDeniedException("User does not have permission to approve pricing suggestions");
            }
        } else if ("inventory".equals(domain)) {
            boolean isOpsManager = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPS_MANAGER"));
            if (!isOpsManager) {
                throw new AccessDeniedException("User does not have permission to approve inventory suggestions");
            }
        } else if ("routing".equals(domain)) {
            boolean isLogistics = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICS_MANAGER"));
            if (!isLogistics) {
                throw new AccessDeniedException("User does not have permission to approve routing suggestions");
            }
        } else if ("risk".equals(domain)) {
            boolean isRisk = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_RISK_ANALYST"));
            if (!isRisk) {
                throw new AccessDeniedException("User does not have permission to approve risk suggestions");
            }
        } else if ("support".equals(domain)) {
            boolean isSupport = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPPORT_LEAD"));
            if (!isSupport) {
                throw new AccessDeniedException("User does not have permission to approve support suggestions");
            }
        }
    }

    private HitlTaskResponse toResponse(AgentSuggestionEntity entity) {
        Double oldValue = null;
        Double newValue = null;
        try {
            JsonNode rec = objectMapper.readTree(entity.getRecommendation());
            if (rec.has("old_value")) {
                oldValue = rec.get("old_value").asDouble();
            }
            if (rec.has("new_value")) {
                newValue = rec.get("new_value").asDouble();
            }
        } catch (Exception e) {
            // ignore
        }

        return HitlTaskResponse.builder()
                .id(entity.getId())
                .traceId(entity.getTraceId())
                .agentName(entity.getAgent() != null ? entity.getAgent().getName() : null)
                .domain(entity.getDomain())
                .entityId(entity.getEntityId())
                .oldValue(oldValue)
                .newValue(newValue)
                .impact(entity.getImpact())
                .confidence(entity.getConfidence())
                .reason(entity.getReason())
                .expiresAt(entity.getExpiresAt())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private void resolveHitlQueueTicket(AgentSuggestionEntity suggestion, boolean approve, String operator, String reason) {
        try {
            List<ch.swissqcommerce.backend.model.HitlQueue> tickets = hitlQueueRepo.findAll();
            for (ch.swissqcommerce.backend.model.HitlQueue ticket : tickets) {
                if ("pending".equalsIgnoreCase(ticket.getStatus()) &&
                        ticket.getType().equals("agent_" + suggestion.getDomain()) &&
                        ticket.getDescription() != null &&
                        ticket.getDescription().contains(suggestion.getReason())) {
                    ticket.setStatus(approve ? "approved" : "voided");
                    ticket.setDescription(
                            ticket.getDescription()
                                    + " | "
                                    + (approve ? "APPROVED" : "VOIDED")
                                    + " by "
                                    + operator
                                    + ": "
                                    + reason);
                    hitlQueueRepo.save(ticket);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update HitlQueue ticket for suggestion ID: {}", suggestion.getId(), e);
        }
    }

    public static class TaskOverrideRequest {
        private String operator;
        private String reason;

        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    @ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleStateDrift(jakarta.persistence.OptimisticLockException ex) {
        return ResponseEntity.status(409).body(new ErrorResponse("STATE_DRIFT", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired")) {
            return ResponseEntity.status(410).body(new ErrorResponse("EXPIRED", ex.getMessage()));
        }
        return ResponseEntity.status(409).body(new ErrorResponse("INVALID_STATE", ex.getMessage()));
    }

    @ExceptionHandler(ch.swissqcommerce.backend.exception.ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ch.swissqcommerce.backend.exception.ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(new ErrorResponse("FORBIDDEN", ex.getMessage()));
    }
}
