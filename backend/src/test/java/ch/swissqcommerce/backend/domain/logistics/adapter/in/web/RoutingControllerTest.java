package ch.swissqcommerce.backend.domain.logistics.adapter.in.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase;
import ch.swissqcommerce.backend.domain.logistics.core.port.in.WarehouseSelectionUseCase.RoutingResult;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.LogisticsDataPort;
import ch.swissqcommerce.backend.domain.logistics.core.port.out.RoutingOrderData;
import ch.swissqcommerce.backend.model.CustomerAddress;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoutingController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RoutingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private LogisticsDataPort logisticsDataPort;
    @MockBean private WarehouseSelectionUseCase warehouseSelectionUseCase;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        var grants = Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "n/a", grants));
    }

    @Test
    public void testRouteOrder_Success() throws Exception {
        authenticateAsAdmin();

        CustomerAddress address =
                CustomerAddress.builder()
                        .addressLine("123 Broadway, 80012")
                        .latitude(new BigDecimal("40.7306"))
                        .longitude(new BigDecimal("-73.9352"))
                        .build();

        RoutingOrderData orderData =
                new RoutingOrderData(101, address, null, Collections.emptyList());
        RoutingResult mockResult =
                new RoutingResult("WH-NY-01", false, 12.50, Collections.emptyList(), "UPS", 2, 1);

        when(logisticsDataPort.findRoutingOrderData(101)).thenReturn(Optional.of(orderData));
        when(warehouseSelectionUseCase.findOptimalWarehouse(orderData))
                .thenReturn(Optional.of(mockResult));

        mockMvc.perform(post("/api/v1/routing/orders/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryWarehouseId").value("WH-NY-01"))
                .andExpect(jsonPath("$.carrier").value("UPS"))
                .andExpect(jsonPath("$.estimatedDeliveryDays").value(2))
                .andExpect(jsonPath("$.packageCount").value(1));
    }

    @Test
    public void testRouteOrder_NotFound() throws Exception {
        authenticateAsAdmin();

        when(logisticsDataPort.findRoutingOrderData(999)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/routing/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found with ID 999"));
    }

    @Test
    public void testRouteOrder_Conflict_Hitl() throws Exception {
        authenticateAsAdmin();

        CustomerAddress address =
                CustomerAddress.builder()
                        .addressLine("123 Broadway, 80012")
                        .latitude(new BigDecimal("40.7306"))
                        .longitude(new BigDecimal("-73.9352"))
                        .build();

        RoutingOrderData orderData =
                new RoutingOrderData(102, address, null, Collections.emptyList());

        when(logisticsDataPort.findRoutingOrderData(102)).thenReturn(Optional.of(orderData));
        when(warehouseSelectionUseCase.findOptimalWarehouse(orderData))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/routing/orders/102"))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error")
                                .value("Automatic routing failed. Order sent to HITL queue."));
    }
}
