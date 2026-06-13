package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.billing.core.model.BillingAccount;
import ch.swissqcommerce.backend.domain.billing.core.model.BillingTier;
import ch.swissqcommerce.backend.domain.billing.port.in.BillingUseCase;
import ch.swissqcommerce.backend.domain.retailer.core.model.Retailer;
import ch.swissqcommerce.backend.domain.retailer.core.service.RetailerServiceImpl;
import ch.swissqcommerce.backend.domain.retailer.port.in.RetailerUseCase;
import ch.swissqcommerce.backend.domain.retailer.port.out.RetailerPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetailerServiceTest {

    @Mock private RetailerPort port;
    @Mock private BillingUseCase billing;

    @InjectMocks private RetailerServiceImpl service;

    @Test
    void register_createsPendingRetailer() {
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));

        Retailer r = service.register("Acme Stores", "ops@acme.test", "store-1", BillingTier.PRO);

        assertNotNull(r.getRetailerId());
        assertEquals("PENDING", r.getStatus());
        assertEquals(BillingTier.PRO, r.getTier());
        assertFalse(r.isApprovalOps());
        assertNull(r.getApiKeyHash());
    }

    @Test
    void register_invalidInputs_rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register("", "a@b.com", "s1", BillingTier.BASIC));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register("X", "no-at-sign", "s1", BillingTier.BASIC));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.register("X", "a@b.com", " ", BillingTier.BASIC));
        verify(port, never()).save(any());
    }

    @Test
    void approveGate_outOfOrder_rejected() {
        Retailer pending = pending("RTL-1");
        when(port.findById("RTL-1")).thenReturn(Optional.of(pending));

        // compliance before ops
        assertThrows(IllegalStateException.class, () -> service.approveGate("RTL-1", "compliance"));
        // admin before ops+compliance
        assertThrows(IllegalStateException.class, () -> service.approveGate("RTL-1", "admin"));
    }

    @Test
    void approveGate_fullApproval_activatesIssuesKeyAndBills() {
        Retailer r = pending("RTL-9");
        when(port.findById("RTL-9")).thenReturn(Optional.of(r));
        when(port.save(any())).thenAnswer(i -> i.getArgument(0));
        when(billing.subscribe("store-1", BillingTier.PRO))
                .thenReturn(BillingAccount.builder().accountId("ACC-1").build());

        assertNull(service.approveGate("RTL-9", "ops").issuedApiKey());
        assertNull(service.approveGate("RTL-9", "compliance").issuedApiKey());
        RetailerUseCase.ApprovalResult activation = service.approveGate("RTL-9", "admin");

        assertEquals("ACTIVE", activation.retailer().getStatus());
        assertNotNull(activation.issuedApiKey());
        assertTrue(activation.issuedApiKey().startsWith("rtl_"));
        assertEquals("ACC-1", activation.retailer().getBillingAccountId());
        assertNotNull(activation.retailer().getApiKeyHash());
        verify(billing, times(1)).subscribe("store-1", BillingTier.PRO);
    }

    @Test
    void approveGate_missingRetailer_notFound() {
        when(port.findById("nope")).thenReturn(Optional.empty());
        assertThrows(
                java.util.NoSuchElementException.class, () -> service.approveGate("nope", "ops"));
    }

    @Test
    void authenticateByApiKey_activeOnly() {
        Retailer active = pending("RTL-2");
        active.setStatus("ACTIVE");
        when(port.findByApiKeyHash(anyString())).thenReturn(Optional.of(active));
        assertTrue(service.authenticateByApiKey("rtl_whatever").isPresent());

        active.setStatus("SUSPENDED");
        assertTrue(service.authenticateByApiKey("rtl_whatever").isEmpty());
    }

    @Test
    void authenticateByApiKey_blankKey_empty() {
        assertTrue(service.authenticateByApiKey("  ").isEmpty());
        verify(port, never()).findByApiKeyHash(any());
    }

    private Retailer pending(String id) {
        return Retailer.builder()
                .retailerId(id)
                .name("Acme")
                .contactEmail("a@b.com")
                .storeId("store-1")
                .tier(BillingTier.PRO)
                .status("PENDING")
                .approvalOps(false)
                .approvalCompliance(false)
                .approvalAdmin(false)
                .build();
    }
}
