package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.governance.core.model.ProcurementApproval;
import ch.swissqcommerce.backend.domain.governance.port.in.GovernanceUseCase;
import ch.swissqcommerce.backend.domain.governance.adapter.out.persistence.ProcurementApprovalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HitlQueueController.class)
@AutoConfigureMockMvc(addFilters = false)
public class HitlQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GovernanceUseCase governanceUseCase;

    @MockBean
    private ProcurementApprovalRepository approvalsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetPendingApprovals() throws Exception {
        ProcurementApproval pending = new ProcurementApproval();
        pending.setId(1);
        pending.setStatus("PENDING");
        pending.setWholesalerId("W1");
        pending.setAmount(new BigDecimal("6000.00"));

        ProcurementApproval approved = new ProcurementApproval();
        approved.setId(2);
        approved.setStatus("APPROVED");

        when(approvalsRepository.findAll()).thenReturn(Arrays.asList(pending, approved));

        mockMvc.perform(get("/api/governance/hitl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    public void testApprove() throws Exception {
        HitlQueueController.OverrideRequest req = new HitlQueueController.OverrideRequest();
        req.setOperator("operator123");
        req.setReason("High demand");

        mockMvc.perform(post("/api/governance/hitl/1/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value("B2B restock transaction successfully overridden and released."));

        verify(governanceUseCase).approveOverride(1, "operator123", "High demand");
    }

    @Test
    public void testReject() throws Exception {
        HitlQueueController.OverrideRequest req = new HitlQueueController.OverrideRequest();
        req.setOperator("operator123");
        req.setReason("Over budget");

        mockMvc.perform(post("/api/governance/hitl/1/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalId").value(1))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value("B2B restock transaction override rejected. Order canceled."));

        verify(governanceUseCase).rejectOverride(1, "operator123", "Over budget");
    }
}
