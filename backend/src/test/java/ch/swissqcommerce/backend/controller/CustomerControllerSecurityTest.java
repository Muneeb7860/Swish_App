package ch.swissqcommerce.backend.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.swissqcommerce.backend.domain.customer.adapter.in.web.CustomerController;
import ch.swissqcommerce.backend.domain.customer.port.in.CustomerUseCase;
import ch.swissqcommerce.backend.domain.transaction.core.model.LedgerLine;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.repository.HitlQueueRepository;
import ch.swissqcommerce.backend.repository.InventoryRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hardening regression tests for {@link CustomerController}'s object-level authorization guard
 * {@code assertOwnership(...)}.
 *
 * <p>Guards against the IDOR where a caller could place orders for, list the orders of, refund the
 * order of, or read the ledger of another customer by passing a {@code customerId} (or operating on
 * an order) they do not own. The guard must reject any principal whose name != the target
 * customerId unless it carries ROLE_ADMIN; the JWT principal name == customerId by convention.
 *
 * <p>Covers the four guarded endpoints that previously had no controller-level authz coverage. The
 * fifth guarded endpoint, {@code purgeProfile}, is covered separately in {@link
 * CustomerControllerTest}. Each endpoint is exercised with the owner / other-customer / admin
 * matrix, and the side-effecting use-case method is verified to never fire on an authz failure.
 *
 * <p>Filters are disabled so we exercise the controller's own ownership check in isolation rather
 * than the gateway/OPA URL-level policy. On the read endpoints (which let the {@code
 * AccessDeniedException} propagate rather than catching it locally) the 403 is produced by {@code
 * GlobalExceptionHandler}, which {@code @WebMvcTest} loads as a {@code @RestControllerAdvice}.
 */
@WebMvcTest(CustomerController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerControllerSecurityTest {

    private static final String OWNER = "CUST-200";
    private static final String OTHER = "CUST-999";
    private static final String ADMIN = "ops-admin";
    private static final int ORDER_ID = 77;

    /** Valid checkout body whose customerId is OWNER — reused across ownership scenarios. */
    private static final String CHECKOUT_BODY =
            """
            {"customerId": "CUST-200", "items": [{"itemId": "SKU-1", "quantity": 2}], "paymentMethod": "Wallet"}
            """;

    /** Valid refund body — ownership is derived from the resolved order, not this body. */
    private static final String REFUND_BODY =
            """
            {"claimReason": "Item missing on delivery", "customerLatitude": 47.3769, "customerLongitude": 8.5417}
            """;

    @Autowired private MockMvc mockMvc;

    // Mirror the collaborator set mocked by CustomerControllerTest so the
    // @WebMvcTest context loads identically.
    @MockBean private InventoryRepository inventoryRepository;
    @MockBean private OrderRepository orderRepository;
    @MockBean private CustomerUseCase customerUseCase;
    @MockBean private HitlQueueRepository hitlQueueRepository;
    @MockBean private OrderUseCase orderUseCase;
    @MockBean private LedgerUseCase ledgerUseCase;

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

    private Order orderOwnedBy(String customerId) {
        return Order.builder()
                .orderId(ORDER_ID)
                .customer(Customer.builder().customerId(customerId).build())
                .status("CONFIRMED")
                .totalAmount(new BigDecimal("25.00"))
                .build();
    }

    private LedgerLine sampleLedgerLine(String actorId) {
        return LedgerLine.builder()
                .lineId(1)
                .accountType("WALLET")
                .actorId(actorId)
                .debit(BigDecimal.ZERO)
                .credit(new BigDecimal("10.00"))
                .build();
    }

    // ---- placeOrder (POST /api/customer/orders) ---------------------------------
    // Ownership is asserted on req.getCustomerId() from the JSON body.

    @Test
    void ownerCanPlaceOwnOrder() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(orderUseCase.checkout(
                        eq(OWNER), anyList(), eq("Wallet"), any(BigDecimal.class), any(), any()))
                .thenReturn(orderOwnedBy(OWNER));

        mockMvc.perform(
                        post("/api/customer/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CHECKOUT_BODY))
                .andExpect(status().isCreated());

        verify(orderUseCase, times(1))
                .checkout(eq(OWNER), anyList(), eq("Wallet"), any(BigDecimal.class), any(), any());
    }

    @Test
    void cannotPlaceOrderForAnotherCustomer() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");

        mockMvc.perform(
                        post("/api/customer/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CHECKOUT_BODY))
                .andExpect(status().isForbidden());

        // The side-effecting checkout must never fire on an authz failure.
        verify(orderUseCase, never()).checkout(any(), any(), any(), any(), any(), any());
    }

    @Test
    void adminMayPlaceOrderForAnyCustomer() throws Exception {
        authenticateAs(ADMIN, "ROLE_ADMIN");
        when(orderUseCase.checkout(
                        eq(OWNER), anyList(), eq("Wallet"), any(BigDecimal.class), any(), any()))
                .thenReturn(orderOwnedBy(OWNER));

        mockMvc.perform(
                        post("/api/customer/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(CHECKOUT_BODY))
                .andExpect(status().isCreated());

        verify(orderUseCase, times(1))
                .checkout(eq(OWNER), anyList(), eq("Wallet"), any(BigDecimal.class), any(), any());
    }

    // ---- getOrders (GET /api/customer/orders?customerId=X) ----------------------

    @Test
    void ownerCanListOwnOrders() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(orderUseCase.getCustomerOrders(OWNER)).thenReturn(List.of(orderOwnedBy(OWNER)));

        mockMvc.perform(get("/api/customer/orders").param("customerId", OWNER))
                .andExpect(status().isOk());

        verify(orderUseCase, times(1)).getCustomerOrders(OWNER);
    }

    @Test
    void cannotListAnotherCustomersOrders() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/customer/orders").param("customerId", OWNER))
                .andExpect(status().isForbidden());

        verify(orderUseCase, never()).getCustomerOrders(anyString());
    }

    @Test
    void adminMayListAnyCustomersOrders() throws Exception {
        authenticateAs(ADMIN, "ROLE_ADMIN");
        when(orderUseCase.getCustomerOrders(OWNER)).thenReturn(List.of(orderOwnedBy(OWNER)));

        mockMvc.perform(get("/api/customer/orders").param("customerId", OWNER))
                .andExpect(status().isOk());

        verify(orderUseCase, times(1)).getCustomerOrders(OWNER);
    }

    // ---- requestRefund (POST /api/customer/orders/{id}/refund) ------------------
    // Ownership is asserted on the resolved order's customerId.

    @Test
    void ownerCanRefundOwnOrder() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(orderUseCase.getOrderById(ORDER_ID)).thenReturn(orderOwnedBy(OWNER));
        when(orderUseCase.requestRefund(
                        eq(ORDER_ID), anyString(), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(Map.of("status", "refund_approved"));

        mockMvc.perform(
                        post("/api/customer/orders/" + ORDER_ID + "/refund")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REFUND_BODY))
                .andExpect(status().isOk());

        verify(orderUseCase, times(1))
                .requestRefund(
                        eq(ORDER_ID), anyString(), any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    void cannotRefundAnotherCustomersOrder() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");
        // Order belongs to OWNER; the OTHER principal must not be able to refund it.
        when(orderUseCase.getOrderById(ORDER_ID)).thenReturn(orderOwnedBy(OWNER));

        mockMvc.perform(
                        post("/api/customer/orders/" + ORDER_ID + "/refund")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REFUND_BODY))
                .andExpect(status().isForbidden());

        verify(orderUseCase, never()).requestRefund(any(), any(), any(), any());
    }

    @Test
    void adminMayRefundAnyCustomersOrder() throws Exception {
        authenticateAs(ADMIN, "ROLE_ADMIN");
        when(orderUseCase.getOrderById(ORDER_ID)).thenReturn(orderOwnedBy(OWNER));
        when(orderUseCase.requestRefund(
                        eq(ORDER_ID), anyString(), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(Map.of("status", "refund_approved"));

        mockMvc.perform(
                        post("/api/customer/orders/" + ORDER_ID + "/refund")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(REFUND_BODY))
                .andExpect(status().isOk());

        verify(orderUseCase, times(1))
                .requestRefund(
                        eq(ORDER_ID), anyString(), any(BigDecimal.class), any(BigDecimal.class));
    }

    // ---- getLedger (GET /api/customer/ledger?customerId=X) ----------------------

    @Test
    void ownerCanReadOwnLedger() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(ledgerUseCase.getCustomerLedger(OWNER)).thenReturn(List.of(sampleLedgerLine(OWNER)));

        mockMvc.perform(get("/api/customer/ledger").param("customerId", OWNER))
                .andExpect(status().isOk());

        verify(ledgerUseCase, times(1)).getCustomerLedger(OWNER);
    }

    @Test
    void cannotReadAnotherCustomersLedger() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/customer/ledger").param("customerId", OWNER))
                .andExpect(status().isForbidden());

        verify(ledgerUseCase, never()).getCustomerLedger(anyString());
    }

    @Test
    void adminMayReadAnyCustomersLedger() throws Exception {
        authenticateAs(ADMIN, "ROLE_ADMIN");
        when(ledgerUseCase.getCustomerLedger(OWNER)).thenReturn(List.of(sampleLedgerLine(OWNER)));

        mockMvc.perform(get("/api/customer/ledger").param("customerId", OWNER))
                .andExpect(status().isOk());

        verify(ledgerUseCase, times(1)).getCustomerLedger(OWNER);
    }
}
