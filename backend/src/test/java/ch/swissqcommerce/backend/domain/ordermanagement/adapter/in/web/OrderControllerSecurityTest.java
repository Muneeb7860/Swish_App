package ch.swissqcommerce.backend.domain.ordermanagement.adapter.in.web;

import ch.swissqcommerce.backend.domain.ordermanagement.port.in.OrderManagementUseCase;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening regression tests for the order-saga trigger (domain 9, E-Commerce Core).
 *
 * Guards against the IDOR where a caller could open an order saga on another
 * customer's behalf by passing an arbitrary {@code customerId} query parameter.
 *
 * Filters are disabled so we exercise the controller's own ownership check in
 * isolation rather than the gateway/OPA URL-level policy.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderManagementUseCase orderUseCase;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String principal, String... authorities) {
        var grants = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", grants));
    }

    @Test
    void ownerCanPlaceOwnOrder() throws Exception {
        authenticateAs("cust-1", "ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/orders").param("orderId", "o-1").param("customerId", "cust-1"))
                .andExpect(status().isAccepted());
        verify(orderUseCase, times(1)).handleOrderCreated("o-1", "cust-1");
    }

    @Test
    void cannotPlaceOrderForAnotherCustomer() throws Exception {
        authenticateAs("cust-1", "ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/orders").param("orderId", "o-1").param("customerId", "cust-2"))
                .andExpect(status().isForbidden());
        // The side-effecting saga must never fire on an authorization failure.
        verify(orderUseCase, never()).handleOrderCreated(anyString(), anyString());
    }

    @Test
    void adminMayActOnBehalfOfAnyCustomer() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        mockMvc.perform(post("/api/v1/orders").param("orderId", "o-9").param("customerId", "cust-2"))
                .andExpect(status().isAccepted());
        verify(orderUseCase, times(1)).handleOrderCreated("o-9", "cust-2");
    }

    @Test
    void blankParamsAreRejected() throws Exception {
        authenticateAs("cust-1", "ROLE_CUSTOMER");
        mockMvc.perform(post("/api/v1/orders").param("orderId", "o-1").param("customerId", ""))
                .andExpect(status().isBadRequest());
        verify(orderUseCase, never()).handleOrderCreated(anyString(), anyString());
    }
}
