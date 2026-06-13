package ch.swissqcommerce.backend.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.domain.governance.adapter.in.web.HitlQueueController;
import ch.swissqcommerce.backend.domain.governance.core.model.HitlItem;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.exception.TicketAlreadyResolvedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer security tests for {@link HitlQueueController} (Phase 8 unified queue).
 *
 * <p>Guards the privilege-escalation hole where any authenticated principal — e.g. a {@code
 * ROLE_CUSTOMER} JWT — could approve or reject human-in-the-loop governance items because the
 * handlers lacked {@code @PreAuthorize}. Every handler requires {@code ROLE_ADMIN}. The unified
 * endpoints serve {@link HitlItem}s and resolve by composite id via {@code resolveHitlItem}.
 *
 * <p>Full {@code @SpringBootTest} context so the app's real {@code @EnableMethodSecurity} genuinely
 * enforces the guard; filters are disabled so we drive the role via a manually-set {@code
 * SecurityContext}. The 403 on denial and 409 on a double-resolve are produced by {@code
 * GlobalExceptionHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class HitlQueueControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private GovernanceUseCase governanceUseCase;

    // Required only to boot the full application context in tests.
    @MockBean private KafkaTemplate<String, String> kafkaTemplate;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String principal, String... authorities) {
        var grants = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, "n/a", grants));
    }

    private String overrideBody(String operator, String reason) throws Exception {
        HitlQueueController.OverrideRequest req = new HitlQueueController.OverrideRequest();
        req.setOperator(operator);
        req.setReason(reason);
        return objectMapper.writeValueAsString(req);
    }

    // ---- GET /api/governance/hitl -----------------------------------------------

    @Test
    void adminCanListPendingHitlItems() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        HitlItem pending =
                HitlItem.builder()
                        .id("PA-1")
                        .source("B2B_PROCUREMENT")
                        .type("b2b_override")
                        .status("PENDING")
                        .amount(new BigDecimal("6000.00"))
                        .description("B2B restock override — wholesaler W1")
                        .build();
        when(governanceUseCase.getPendingHitlItems()).thenReturn(List.of(pending));

        mockMvc.perform(get("/api/governance/hitl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("PA-1"))
                .andExpect(jsonPath("$[0].source").value("B2B_PROCUREMENT"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void customerCannotListPendingHitlItems() throws Exception {
        authenticateAs("CUST-200", "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/governance/hitl")).andExpect(status().isForbidden());

        verify(governanceUseCase, never()).getPendingHitlItems();
    }

    // ---- POST /api/governance/hitl/{id}/approve ---------------------------------

    @Test
    void adminCanApprove() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");

        mockMvc.perform(
                        post("/api/governance/hitl/PA-1/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody("operator123", "High demand")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("PA-1"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("HITL item approved and released."));

        verify(governanceUseCase).resolveHitlItem("PA-1", true, "operator123", "High demand");
    }

    @Test
    void customerCannotApprove() throws Exception {
        authenticateAs("CUST-200", "ROLE_CUSTOMER");

        mockMvc.perform(
                        post("/api/governance/hitl/PA-1/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody("CUST-200", "I want this released")))
                .andExpect(status().isForbidden());

        // The privileged override must never fire for a non-admin principal.
        verify(governanceUseCase, never())
                .resolveHitlItem(anyString(), anyBoolean(), anyString(), anyString());
    }

    // ---- POST /api/governance/hitl/{id}/reject ----------------------------------

    @Test
    void adminCanReject() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");

        mockMvc.perform(
                        post("/api/governance/hitl/AQ-HITL-XYZ/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody("operator123", "Over budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("AQ-HITL-XYZ"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("HITL item rejected."));

        verify(governanceUseCase)
                .resolveHitlItem("AQ-HITL-XYZ", false, "operator123", "Over budget");
    }

    @Test
    void customerCannotReject() throws Exception {
        authenticateAs("CUST-200", "ROLE_CUSTOMER");

        mockMvc.perform(
                        post("/api/governance/hitl/PA-1/reject")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody("CUST-200", "cancel this")))
                .andExpect(status().isForbidden());

        verify(governanceUseCase, never())
                .resolveHitlItem(anyString(), anyBoolean(), anyString(), anyString());
    }

    // ---- Idempotency: an item already resolved cannot be re-processed -----------

    @Test
    void doubleApproveIsIdempotentConflict() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        // Second approve of an already-resolved item: the use-case rejects the
        // re-process; the controller must surface 409, not silently re-run it.
        doThrow(new TicketAlreadyResolvedException("Ticket is already APPROVED"))
                .when(governanceUseCase)
                .resolveHitlItem("PA-1", true, "operator123", "High demand");

        mockMvc.perform(
                        post("/api/governance/hitl/PA-1/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(overrideBody("operator123", "High demand")))
                .andExpect(status().isConflict());
    }
}
