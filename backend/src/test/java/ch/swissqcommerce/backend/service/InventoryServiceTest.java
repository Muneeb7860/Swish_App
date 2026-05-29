package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderRepository;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PickerRepository pickerRepository;
    @Mock private RiderRepository riderRepository;
    @Mock private SecurityTrustLedgerRepository trustLedgerRepository;

    @InjectMocks private InventoryService inventoryService;

    @Test
    public void testRebalanceStock_Success() {
        Inventory source = new Inventory();
        source.setItemId("ITEM-1");
        source.setName("Apple");
        source.setStock(20);

        Inventory target = new Inventory();
        target.setItemId("ITEM-2");
        target.setName("Apple");
        target.setStock(5);

        when(inventoryRepository.findByStoreStoreId("STORE-1")).thenReturn(List.of(source));
        when(inventoryRepository.findByStoreStoreId("STORE-2")).thenReturn(List.of(target));

        Map<String, Object> result = inventoryService.rebalanceStock("ITEM-1", "STORE-1", "STORE-2", 10);

        assertEquals(10, source.getStock());
        assertEquals(15, target.getStock());
        verify(inventoryRepository, times(2)).save(any(Inventory.class));
    }

    @Test
    public void testHandoverToRider_Success_LightningBadge() {
        Order order = new Order();
        order.setStatus("picking");

        Picker picker = new Picker();
        picker.setTrustScore(60);
        picker.setLightningBadge(false);

        Rider rider = new Rider();
        rider.setTrustScore(60);

        when(orderRepository.findById(1)).thenReturn(Optional.of(order));
        when(pickerRepository.findById("PICKER-1")).thenReturn(Optional.of(picker));
        when(riderRepository.findById("RIDER-1")).thenReturn(Optional.of(rider));

        Map<String, Object> result = inventoryService.handoverToRider(1, "PICKER-1", "RIDER-1", 50);

        assertEquals("shipping", order.getStatus());
        assertTrue(picker.getLightningBadge());
        verify(trustLedgerRepository, times(1)).save(any(SecurityTrustLedger.class));
    }
}
