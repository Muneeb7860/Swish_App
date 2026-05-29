package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WholesalerServiceTest {

    @Mock private WholesalerRepository wholesalerRepository;
    @Mock private B2BRestockOrderRepository restockOrderRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private LedgerUseCase ledgerService;

    @InjectMocks private WholesalerService wholesalerService;

    @Test
    public void testCreateRestockOrder_AcademyDiscount() {
        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        w.setIsActive(true);
        w.setTrustScore(80);
        w.setBaseInvoiceAmount(new BigDecimal("1000.00"));
        w.setAcademyDiscountActive(true);

        when(wholesalerRepository.findById("W-1")).thenReturn(Optional.of(w));
        when(restockOrderRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        B2BRestockOrder order = wholesalerService.createRestockOrder("STORE-1", "W-1", null);

        assertNotNull(order);
        assertEquals(0, new BigDecimal("900.00").compareTo(order.getInvoiceAmount()));
        assertFalse(order.getIsFallback());
    }

    @Test
    public void testFulfillRestock() {
        Wholesaler w = new Wholesaler();
        w.setWholesalerId("W-1");
        w.setName("Test W");

        B2BRestockOrder order = new B2BRestockOrder();
        order.setStatus("pending");
        order.setWholesaler(w);
        order.setInvoiceAmount(new BigDecimal("900.00"));

        when(restockOrderRepository.findById(1)).thenReturn(Optional.of(order));

        Map<String, Object> res = wholesalerService.fulfillRestock(1);

        assertEquals("fulfilled", order.getStatus());
        verify(ledgerService, times(1)).recordTransaction(anyString(), anyString(), anyList());
        assertEquals("fulfilled", res.get("status"));
    }
}
