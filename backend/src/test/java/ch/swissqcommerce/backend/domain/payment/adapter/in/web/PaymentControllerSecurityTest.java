package ch.swissqcommerce.backend.domain.payment.adapter.in.web;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentProcessingUseCase;
import ch.swissqcommerce.backend.domain.payment.port.in.PaymentUseCase;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hardening regression tests for the payment controller's object-level
 * authorization guard (domain 9, E-Commerce Core).
 *
 * Guards against the IDOR where a caller could authorize, list, or read
 * payments belonging to another customer by passing an arbitrary
 * {@code customerId} (or payment id) that they do not own. The controller's
 * {@code assertPaymentOwnership(...)} check must reject any principal whose
 * name != the payment's customerId unless it carries ROLE_ADMIN.
 *
 * Filters are disabled so we exercise the controller's own ownership check in
 * isolation rather than the gateway/OPA URL-level policy.
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerSecurityTest {

    private static final String OWNER = "CUST-200";
    private static final String OTHER = "CUST-999";

    /** Valid authorize body for OWNER's payment — reused across ownership scenarios. */
    private static final String AUTHORIZE_BODY = """
            {"orderId": 42, "customerId": "CUST-200", "amount": 25.00, "paymentMethod": "Wallet"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentUseCase paymentUseCase;

    @MockBean
    private PaymentProcessingUseCase paymentProcessingUseCase;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String principal, String... authorities) {
        var grants = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", grants));
    }

    private Payment samplePayment(String customerId) {
        return Payment.builder()
                .paymentId(1)
                .orderId(42)
                .customerId(customerId)
                .amount(new BigDecimal("25.00"))
                .currency("CHF")
                .paymentMethod("Wallet")
                .status("AUTHORIZED")
                .build();
    }

    // ---- authorize (POST /api/payments) -----------------------------------------

    @Test
    void ownerCanAuthorizeOwnPayment() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(paymentUseCase.authorizePayment(anyInt(), eq(OWNER), any(BigDecimal.class), anyString(), any()))
                .thenReturn(samplePayment(OWNER));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTHORIZE_BODY))
                .andExpect(status().isCreated());

        verify(paymentUseCase, times(1))
                .authorizePayment(eq(42), eq(OWNER), any(BigDecimal.class), eq("Wallet"), any());
    }

    @Test
    void cannotAuthorizePaymentForAnotherCustomer() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTHORIZE_BODY))
                .andExpect(status().isForbidden());

        // The side-effecting authorization must never fire on an authz failure.
        verify(paymentUseCase, never()).authorizePayment(any(), any(), any(), any(), any());
    }

    @Test
    void adminMayAuthorizeOnBehalfOfAnyCustomer() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        when(paymentUseCase.authorizePayment(anyInt(), eq(OWNER), any(BigDecimal.class), anyString(), any()))
                .thenReturn(samplePayment(OWNER));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(AUTHORIZE_BODY))
                .andExpect(status().isCreated());

        verify(paymentUseCase, times(1))
                .authorizePayment(eq(42), eq(OWNER), any(BigDecimal.class), eq("Wallet"), any());
    }

    // ---- listPayments (GET /api/payments?customerId=X) --------------------------

    @Test
    void ownerCanListOwnPayments() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(paymentUseCase.getPaymentsByCustomer(OWNER)).thenReturn(List.of(samplePayment(OWNER)));

        mockMvc.perform(get("/api/payments").param("customerId", OWNER))
                .andExpect(status().isOk());

        verify(paymentUseCase, times(1)).getPaymentsByCustomer(OWNER);
    }

    @Test
    void cannotListAnotherCustomersPayments() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");

        mockMvc.perform(get("/api/payments").param("customerId", OWNER))
                .andExpect(status().isForbidden());

        verify(paymentUseCase, never()).getPaymentsByCustomer(anyString());
    }

    @Test
    void adminMayListAnyCustomersPayments() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        when(paymentUseCase.getPaymentsByCustomer(OWNER)).thenReturn(List.of(samplePayment(OWNER)));

        mockMvc.perform(get("/api/payments").param("customerId", OWNER))
                .andExpect(status().isOk());

        verify(paymentUseCase, times(1)).getPaymentsByCustomer(OWNER);
    }

    // ---- getPayment (GET /api/payments/{id}) ------------------------------------

    @Test
    void ownerCanGetOwnPayment() throws Exception {
        authenticateAs(OWNER, "ROLE_CUSTOMER");
        when(paymentUseCase.getPayment(1)).thenReturn(samplePayment(OWNER));

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk());
    }

    @Test
    void cannotGetAnotherCustomersPayment() throws Exception {
        authenticateAs(OTHER, "ROLE_CUSTOMER");
        when(paymentUseCase.getPayment(1)).thenReturn(samplePayment(OWNER));

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminMayGetAnyCustomersPayment() throws Exception {
        authenticateAs("ops-admin", "ROLE_ADMIN");
        when(paymentUseCase.getPayment(1)).thenReturn(samplePayment(OWNER));

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk());
    }
}
