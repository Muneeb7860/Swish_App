package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private DarkStoreRepository darkStoreRepository;
    @Mock
    private RiderRepository riderRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private SystemConfigurationRepository systemConfigurationRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private OutboxEventRepository outboxRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    public void testCheckout_Success() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST-1");
        customer.setLoyaltyPoints(0);

        DarkStore store = new DarkStore();
        store.setStoreId("Central Store");
        store.setStoreName("Central Store");

        Inventory inventory = new Inventory();
        inventory.setItemId("ITEM-1");
        inventory.setName("Apple");
        inventory.setPrice(new BigDecimal("1.50"));
        inventory.setStock(10);
        inventory.setStore(store);

        Rider rider = new Rider();
        rider.setRiderId("RIDER-1");
        rider.setOnboardingStatus("active");

        when(customerRepository.findById("CUST-1")).thenReturn(Optional.of(customer));
        when(inventoryRepository.findById("ITEM-1")).thenReturn(Optional.of(inventory));
        when(darkStoreRepository.findById("Central Store")).thenReturn(Optional.of(store));
        when(riderRepository.findAll()).thenReturn(List.of(rider));
        when(systemConfigurationRepository.findById("current_weather")).thenReturn(Optional.empty());
        when(systemConfigurationRepository.findById("central_picker_backlog")).thenReturn(Optional.empty());
        
        Order savedOrder = new Order();
        savedOrder.setOrderId(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setOrderId(1);
            return o;
        });

        Order result = orderService.checkout("CUST-1", List.of(new OrderService.CartItem("ITEM-1", 2)), "Swipe", BigDecimal.ZERO, 0, "IDEM-KEY-1");

        assertNotNull(result);
        assertEquals(1, result.getOrderId());
        verify(ledgerService, times(1)).recordTransaction(anyString(), anyString(), anyList());
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
        assertEquals(8, inventory.getStock());
    }
}
