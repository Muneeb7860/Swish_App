package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.model.OutboxEvent;
import ch.swissqcommerce.backend.domain.transaction.core.service.OrderServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

    @Mock private OrderRepository orderRepository;
    @Mock private CustomerPort customerPort;
    @Mock private DarkStorePort darkStorePort;
    @Mock private RiderPort riderPort;
    @Mock private InventoryPort inventoryPort;
    @Mock private SystemConfigPort systemConfigPort;
    @Mock private LedgerUseCase ledgerUseCase;
    @Mock private OutboxEventPort outboxEventPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private OrderServiceImpl orderService;

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

        when(customerPort.findCustomerById("CUST-1")).thenReturn(Optional.of(customer));
        when(inventoryPort.findInventoryById("ITEM-1")).thenReturn(Optional.of(inventory));
        when(darkStorePort.findDarkStoreById("Central Store")).thenReturn(Optional.of(store));
        when(riderPort.findAll()).thenReturn(List.of(rider));
        when(systemConfigPort.getSystemConfig("current_weather", "Sunny")).thenReturn("Sunny");
        when(systemConfigPort.getSystemConfig("central_picker_backlog", "0")).thenReturn("0");
        
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setOrderId(1);
            return o;
        });

        Order result = orderService.checkout("CUST-1", List.of(new OrderUseCase.CartItem("ITEM-1", 2)), "Swipe", BigDecimal.ZERO, 0, "IDEM-KEY-1");

        assertNotNull(result);
        assertEquals(1, result.getOrderId());
        verify(ledgerUseCase, times(1)).recordTransaction(anyString(), anyString(), anyList());
        verify(outboxEventPort, times(1)).save(any(OutboxEvent.class));
        assertEquals(8, inventory.getStock());
    }
}
