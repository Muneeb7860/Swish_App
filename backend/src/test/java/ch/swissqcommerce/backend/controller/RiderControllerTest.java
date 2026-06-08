package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.enrollment.adapter.in.web.RiderController;
import ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase;
import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiderController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RiderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiderUseCase riderService;

    @Autowired
    private ObjectMapper objectMapper;

    public static class OnboardingRequest {
        private String name;
        private String vehicleType;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVehicleType() { return vehicleType; }
        public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    }

    @Test
    public void testSubmitOnboarding() throws Exception {
        OnboardingRequest req = new OnboardingRequest();
        req.setName("John");
        req.setVehicleType("Bike");

        when(riderService.submitOnboarding("John", "Bike", null))
                .thenReturn(Map.of("status", "submitted"));

        mockMvc.perform(post("/api/rider/onboard")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("submitted"));
    }

    @Test
    public void testGetAcademyCourses() throws Exception {
        when(riderService.getAcademyCourses()).thenReturn(List.of());
        mockMvc.perform(get("/api/rider/academy/courses"))
                .andExpect(status().isOk());
    }

    @Test
    public void testCompleteCourse() throws Exception {
        when(riderService.completeAcademyCourse("R1", "COURSE_001"))
                .thenReturn(Map.of("status", "course_completed"));

        mockMvc.perform(post("/api/rider/academy/courses/COURSE_001/complete?riderId=R1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("course_completed"));
    }

    @Test
    public void testApproveOnboarding() throws Exception {
        when(riderService.approveOnboarding("APP1", "ops"))
                .thenReturn(Map.of("status", "gate_approved"));

        mockMvc.perform(post("/api/rider/onboard/APP1/approve?gate=ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("gate_approved"));
    }
}
