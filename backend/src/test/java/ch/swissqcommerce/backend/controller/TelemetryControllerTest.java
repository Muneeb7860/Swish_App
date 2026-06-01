package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.service.InMemoryGeoStore;
import ch.swissqcommerce.backend.service.TelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TelemetryService telemetryService;

    @MockBean
    private InMemoryGeoStore geoStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testIngestTick() throws Exception {
        TelemetryController.TelemetryTickRequest req = new TelemetryController.TelemetryTickRequest();
        req.setOrderId(1);
        req.setLatitude(new BigDecimal("47.3769"));
        req.setLongitude(new BigDecimal("8.5417"));
        req.setTemperature(new BigDecimal("9.0"));
        req.setDryIceInjected(false);

        OrderTelemetryLog log = new OrderTelemetryLog();
        log.setLogId(10);

        when(telemetryService.recordTelemetry(eq(1), any(), any(), any(), eq(false))).thenReturn(log);

        mockMvc.perform(post("/api/telemetry/tick")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.persisted").value(true))
                .andExpect(jsonPath("$.alertTriggered").value(true));
    }

    @Test
    public void testStreamTelemetry() throws Exception {
        when(geoStore.getLatestLocation(1)).thenReturn(
            new InMemoryGeoStore.RiderLocation(
                new BigDecimal("47.3769"), new BigDecimal("8.5417"), new BigDecimal("5.0")
            )
        );

        mockMvc.perform(get("/api/telemetry/stream/1"))
                .andExpect(status().isOk());
    }

    @Test
    public void testInjectDryIce() throws Exception {
        when(geoStore.getLatestLocation(1)).thenReturn(null);

        mockMvc.perform(post("/api/telemetry/1/dry-ice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.dryIceInjected").value(true));
    }
}
