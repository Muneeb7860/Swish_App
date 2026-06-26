package ch.swissqcommerce.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.domain.governance.adapter.in.web.HitlTaskController;
import ch.swissqcommerce.backend.gateway.ExecutionGateway;
import ch.swissqcommerce.backend.model.AgentSuggestionEntity;
import ch.swissqcommerce.backend.model.PolicyDecision;
import ch.swissqcommerce.backend.repository.AgentSuggestionEntityRepository;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.PolicyDecisionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HitlTaskController.class)
@AutoConfigureMockMvc(addFilters = false)
public class HitlTaskControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AgentSuggestionEntityRepository agentSuggestionRepo;

    @MockBean private ExecutionGateway executionGateway;

    @MockBean private HitlQueueRepository hitlQueueRepo;

    @MockBean private PolicyDecisionRepository policyDecisionRepo;

    @MockBean private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String principal, String... authorities) {
        var grants = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, "n/a", grants));
    }

    @Test
    public void testList_PricingManager_ReturnsOnlyPricingDomain() throws Exception {
        authenticateAs("pricing_mgr", "ROLE_PRICING_MANAGER");

        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(UUID.randomUUID())
                        .domain("pricing")
                        .recommendation("{\"old_value\":10.00,\"new_value\":10.50}")
                        .status("pending")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .createdAt(OffsetDateTime.now())
                        .build();

        when(agentSuggestionRepo.findByStatusAndDomain(
                        eq("pending"), eq("pricing"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(suggestion)));

        mockMvc.perform(get("/api/v1/hitl/tasks?status=pending&assignee_role=pricing_manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].domain").value("pricing"))
                .andExpect(jsonPath("$.content[0].oldValue").value(10.00))
                .andExpect(jsonPath("$.content[0].newValue").value(10.50));
    }

    @Test
    public void testApprove_Success_WritesExecutionRecord() throws Exception {
        authenticateAs("pricing_mgr", "ROLE_PRICING_MANAGER");

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .recommendation("{\"old_value\":10.00,\"new_value\":10.50}")
                        .status("pending")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));
        // Mock execute approved action
        doAnswer(
                        invocation -> {
                            suggestion.setStatus("executed");
                            return null;
                        })
                .when(executionGateway)
                .execute(eq(suggestionId), eq("pricing_mgr"));

        HitlTaskController.TaskOverrideRequest req = new HitlTaskController.TaskOverrideRequest();
        req.setOperator("pricing_mgr");
        req.setReason("Competitor matches");

        mockMvc.perform(
                        post("/api/v1/hitl/tasks/" + suggestionId + "/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("executed"));

        verify(executionGateway).execute(eq(suggestionId), eq("pricing_mgr"));
        verify(policyDecisionRepo).save(any(PolicyDecision.class));
    }

    @Test
    public void testApprove_StateDrift_Returns409() throws Exception {
        authenticateAs("pricing_mgr", "ROLE_PRICING_MANAGER");

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .recommendation("{\"old_value\":10.00,\"new_value\":10.50}")
                        .status("pending")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        // Throw OptimisticLockException to simulate state drift
        doThrow(new jakarta.persistence.OptimisticLockException("Price changed since suggestion"))
                .when(executionGateway)
                .execute(eq(suggestionId), eq("pricing_mgr"));

        HitlTaskController.TaskOverrideRequest req = new HitlTaskController.TaskOverrideRequest();
        req.setOperator("pricing_mgr");
        req.setReason("Competitor matches");

        mockMvc.perform(
                        post("/api/v1/hitl/tasks/" + suggestionId + "/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STATE_DRIFT"))
                .andExpect(jsonPath("$.message").value("Price changed since suggestion"));
    }

    @Test
    public void testApprove_AlreadyApproved_Returns200_Idempotent() throws Exception {
        authenticateAs("pricing_mgr", "ROLE_PRICING_MANAGER");

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .recommendation("{\"old_value\":10.00,\"new_value\":10.50}")
                        .status("executed") // already executed!
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        HitlTaskController.TaskOverrideRequest req = new HitlTaskController.TaskOverrideRequest();
        req.setOperator("pricing_mgr");
        req.setReason("Competitor matches");

        mockMvc.perform(
                        post("/api/v1/hitl/tasks/" + suggestionId + "/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("executed"));

        // Verify gateway execution was NEVER called again
        verify(executionGateway, never()).execute(any(UUID.class), anyString());
    }

    @Test
    public void testApprove_Expired_Returns410() throws Exception {
        authenticateAs("pricing_mgr", "ROLE_PRICING_MANAGER");

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .recommendation("{\"old_value\":10.00,\"new_value\":10.50}")
                        .status("pending")
                        .expiresAt(OffsetDateTime.now().minusMinutes(5)) // expired!
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        HitlTaskController.TaskOverrideRequest req = new HitlTaskController.TaskOverrideRequest();
        req.setOperator("pricing_mgr");
        req.setReason("Expired action");

        mockMvc.perform(
                        post("/api/v1/hitl/tasks/" + suggestionId + "/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("EXPIRED"));

        verify(executionGateway, never()).execute(any(UUID.class), anyString());
    }

    @Test
    public void testReject_Success_NoExecutionRecord() throws Exception {
        authenticateAs("pricing_mgr", "ROLE_PRICING_MANAGER");

        UUID suggestionId = UUID.randomUUID();
        AgentSuggestionEntity suggestion =
                AgentSuggestionEntity.builder()
                        .id(suggestionId)
                        .domain("pricing")
                        .recommendation("{\"old_value\":10.00,\"new_value\":10.50}")
                        .status("pending")
                        .expiresAt(OffsetDateTime.now().plusHours(1))
                        .build();

        when(agentSuggestionRepo.findById(suggestionId)).thenReturn(Optional.of(suggestion));

        HitlTaskController.TaskOverrideRequest req = new HitlTaskController.TaskOverrideRequest();
        req.setOperator("pricing_mgr");
        req.setReason("Bad pricing decision");

        mockMvc.perform(
                        post("/api/v1/hitl/tasks/" + suggestionId + "/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));

        verify(agentSuggestionRepo).save(eq(suggestion));
        verify(policyDecisionRepo).save(any(PolicyDecision.class));
        verify(executionGateway, never()).execute(any(UUID.class), anyString());
    }
}
