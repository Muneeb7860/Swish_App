package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.ChaosFaultLog;
import ch.swissqcommerce.backend.model.HitlQueue;
import ch.swissqcommerce.backend.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testInjectFault() throws Exception {
        AdminController.InjectFaultRequest req = new AdminController.InjectFaultRequest();
        req.setFaultType("API_DELAY");
        req.setDetails("Delaying inventory APIs");

        ChaosFaultLog log = new ChaosFaultLog();
        log.setFaultId(1);
        log.setFaultType("API_DELAY");

        when(adminService.injectFault(eq("API_DELAY"), eq("Delaying inventory APIs"))).thenReturn(log);

        mockMvc.perform(post("/api/admin/chaos/faults")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faultId").value(1))
                .andExpect(jsonPath("$.faultType").value("API_DELAY"));
    }

    @Test
    public void testResolveFault() throws Exception {
        ChaosFaultLog log = new ChaosFaultLog();
        log.setFaultId(1);
        log.setResolvedAt(OffsetDateTime.now());

        when(adminService.resolveFault(1)).thenReturn(log);

        mockMvc.perform(post("/api/admin/chaos/1/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faultId").value(1));
    }

    @Test
    public void testGetActiveFaults() throws Exception {
        when(adminService.getActiveFaults()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/chaos/active"))
                .andExpect(status().isOk());
    }

    @Test
    public void testApproveOnboarding() throws Exception {
        AdminController.OnboardingApprovalRequest req = new AdminController.OnboardingApprovalRequest();
        req.setGate("BACKGROUND_CHECK");

        when(adminService.approveOnboarding("APP_1", "BACKGROUND_CHECK"))
                .thenReturn(Map.of("status", "approved"));

        mockMvc.perform(post("/api/admin/onboard/queue/APP_1/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
    }

    @Test
    public void testGetPendingHitlTickets() throws Exception {
        when(adminService.getPendingHitlTickets()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/hitl/queue"))
                .andExpect(status().isOk());
    }

    @Test
    public void testResolveHitlTicket() throws Exception {
        AdminController.HitlDecisionRequest req = new AdminController.HitlDecisionRequest();
        req.setDecision("approve");
        req.setReason("Looks good");

        when(adminService.resolveHitlTicket("T1", "approve", "Looks good"))
                .thenReturn(Map.of("status", "resolved"));

        mockMvc.perform(post("/api/admin/hitl/queue/T1/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));
    }

    @Test
    public void testGetSystemHealth() throws Exception {
        when(adminService.getSystemHealth()).thenReturn(Map.of("status", "UP"));

        mockMvc.perform(get("/api/admin/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
