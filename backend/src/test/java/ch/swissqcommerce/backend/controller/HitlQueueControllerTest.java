package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.governance.adapter.in.web.HitlQueueController;
import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.exception.TicketAlreadyResolvedException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.math.BigDecimal;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer security tests for {@link HitlQueueController}.
 *
 * <p>Guards the privilege-escalation hole where any authenticated principal —
 * e.g. a {@code ROLE_CUSTOMER} JWT — could approve or reject human-in-the-loop
 * governance tickets (including B2B procurement overrides) because the handlers
 * lacked {@code @PreAuthorize}. Every handler now requires {@code ROLE_ADMIN}.
 *
 * <p>The prior version of this test set {@code addFilters = false} on a
 * {@code @WebMvcTest} slice and never authenticated, so {@code @PreAuthorize} was
 * never evaluated — it would have passed even with the annotations removed. We use
 * a full {@code @SpringBootTest} context here so the app's real
 * {@code @EnableMethodSecurity} genuinely enforces the guard (a method-security
 * slice drops the proxied controller's request mappings, yielding spurious 404s).
 * {@code GovernanceUseCase} is mocked to keep this a focused web-layer test;
 * filters are disabled so we drive the role via a manually-set {@code SecurityContext}
 * and exercise the controller's own {@code @PreAuthorize} in isolation. The 403 on
 * denial and 409 on a double-approve are produced by {@code GlobalExceptionHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class HitlQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GovernanceUseCase governanceUseCase;

    // Required only to boot the full application context in tests.
    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String principal, String... authorities) {
        var grants = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
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
    void adminCanListPendingApprovals() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        ProcurementApproval pending = new ProcurementApproval();
        pending.setId(1);
        pending.setStatus("PENDING");
        pending.setWholesalerId("W1");
        pending.setAmount(new BigDecimal("6000.00"));
        when(governanceUseCase.getPendingApprovals()).thenReturn(Arrays.asList(pending));

        mockMvc.perform(get("/api/governance/hitl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void customerCannotListPendingApprovals() throws Exception {
        authenticateAs("CUST-200", "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/governance/hitl"))
                .andExpect(status().isForbidden());

        verify(governanceUseCase, never()).getPendingApprovals();
    }

    // ---- POST /api/governance/hitl/{id}/approve ---------------------------------

    @Test
    void adminCanApprove() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");

        mockMvc.perform(post("/api/governance/hitl/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("operator123", "High demand")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("B2B restock transaction successfully overridden and released."));

        verify(governanceUseCase).approveOverride(1, "operator123", "High demand");
    }

    @Test
    void customerCannotApprove() throws Exception {
        authenticateAs("CUST-200", "ROLE_CUSTOMER");

        mockMvc.perform(post("/api/governance/hitl/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("CUST-200", "I want this released")))
                .andExpect(status().isForbidden());

        // The privileged override must never fire for a non-admin principal.
        verify(governanceUseCase, never()).approveOverride(anyInt(), anyString(), anyString());
    }

    // ---- POST /api/governance/hitl/{id}/reject ----------------------------------

    @Test
    void adminCanReject() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");

        mockMvc.perform(post("/api/governance/hitl/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("operator123", "Over budget")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(1))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("B2B restock transaction override rejected. Order canceled."));

        verify(governanceUseCase).rejectOverride(1, "operator123", "Over budget");
    }

    @Test
    void customerCannotReject() throws Exception {
        authenticateAs("CUST-200", "ROLE_CUSTOMER");

        mockMvc.perform(post("/api/governance/hitl/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("CUST-200", "cancel this")))
                .andExpect(status().isForbidden());

        verify(governanceUseCase, never()).rejectOverride(anyInt(), anyString(), anyString());
    }

    // ---- Idempotency: a ticket already resolved cannot be re-processed ----------

    @Test
    void doubleApproveIsIdempotentConflict() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        // Second approve of an already-APPROVED ticket: the use-case rejects the
        // re-process; the controller must surface 409, not silently re-run it.
        doThrow(new TicketAlreadyResolvedException("Ticket is already APPROVED"))
                .when(governanceUseCase).approveOverride(1, "operator123", "High demand");

        mockMvc.perform(post("/api/governance/hitl/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideBody("operator123", "High demand")))
                .andExpect(status().isConflict());
    }
}
