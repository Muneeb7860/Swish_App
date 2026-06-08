package ch.swissqcommerce.backend.controller;

import ch.swissqcommerce.backend.domain.telemetry.adapter.in.web.TelemetryController;
import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import ch.swissqcommerce.backend.domain.telemetry.port.in.TelemetryUseCase;
import ch.swissqcommerce.backend.domain.telemetry.port.out.TelemetryPort;
import ch.swissqcommerce.backend.service.InMemoryGeoStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

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
    private TelemetryUseCase telemetryService;

    @MockBean
    private InMemoryGeoStore geoStore;

    @MockBean
    private TelemetryPort telemetryPort;

    @MockBean
    private ch.swissqcommerce.backend.repository.OrderRepository orderRepository;

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

        OrderTelemetryLogEntity log = new OrderTelemetryLogEntity();
        log.setLogId(10);

        when(telemetryService.recordTelemetry(eq(1), any(), any(), any(), eq(false))).thenReturn(log);
        when(telemetryService.isThermalBreachActive(eq(1), any())).thenReturn(true);

        mockMvc.perform(post("/api/telemetry/tick")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.persisted").value(true))
                .andExpect(jsonPath("$.alertTriggered").value(true))
                .andExpect(jsonPath("$.thermalBreachActive").value(true));
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
