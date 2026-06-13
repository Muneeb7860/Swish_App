package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.core.service.OrderServiceImpl;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.in.OrderUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.model.OutboxEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock private OrderPort orderPort;
    @Mock private CustomerPort customerPort;
    @Mock private DarkStorePort darkStorePort;
    @Mock private RiderPort riderPort;
    @Mock private InventoryPort inventoryPort;
    @Mock private SystemConfigPort systemConfigPort;
    @Mock private LedgerUseCase ledgerUseCase;
    @Mock private OutboxEventPort outboxEventPort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private HitlQueuePort hitlQueuePort;

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

        when(orderPort.save(any(Order.class)))
                .thenAnswer(
                        invocation -> {
                            Order o = invocation.getArgument(0);
                            o.setOrderId(1);
                            return o;
                        });

        Order result =
                orderService.checkout(
                        "CUST-1",
                        List.of(new OrderUseCase.CartItem("ITEM-1", 2)),
                        "Swipe",
                        BigDecimal.ZERO,
                        0,
                        "IDEM-KEY-1");

        assertNotNull(result);
        assertEquals(1, result.getOrderId());
        verify(ledgerUseCase, times(1)).recordTransaction(anyString(), anyString(), anyList());
        verify(outboxEventPort, times(1)).save(any(OutboxEvent.class));
        assertEquals(8, inventory.getStock());
    }

    @Test
    public void testCheckout_PerishableScooterAssignmentAndSlaDeduction() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST-1");
        customer.setLoyaltyPoints(0);

        DarkStore store = new DarkStore();
        store.setStoreId("Central Store");
        store.setStoreName("Central Store");

        Inventory inventory = new Inventory();
        inventory.setItemId("ITEM-1");
        inventory.setName("Fresh Milk");
        inventory.setPrice(new BigDecimal("2.50"));
        inventory.setStock(10);
        inventory.setStore(store);
        inventory.setPerishable(true);

        Rider ebikeRider = new Rider();
        ebikeRider.setRiderId("RIDER-BIKE");
        ebikeRider.setVehicleType("E-Bike");
        ebikeRider.setOnboardingStatus("active");

        Rider scooterRider = new Rider();
        scooterRider.setRiderId("RIDER-SCOOTER");
        scooterRider.setVehicleType("Scooter");
        scooterRider.setOnboardingStatus("active");

        when(customerPort.findCustomerById("CUST-1")).thenReturn(Optional.of(customer));
        when(inventoryPort.findInventoryById("ITEM-1")).thenReturn(Optional.of(inventory));
        when(darkStorePort.findDarkStoreById("Central Store")).thenReturn(Optional.of(store));
        when(riderPort.findAll()).thenReturn(List.of(ebikeRider, scooterRider));
        when(systemConfigPort.getSystemConfig("current_weather", "Sunny")).thenReturn("Sunny");
        when(systemConfigPort.getSystemConfig("central_picker_backlog", "0")).thenReturn("0");

        when(orderPort.save(any(Order.class)))
                .thenAnswer(
                        invocation -> {
                            Order o = invocation.getArgument(0);
                            o.setOrderId(2);
                            return o;
                        });

        Order result =
                orderService.checkout(
                        "CUST-1",
                        List.of(new OrderUseCase.CartItem("ITEM-1", 1)),
                        "Swipe",
                        BigDecimal.ZERO,
                        0,
                        "IDEM-KEY-2");

        assertNotNull(result);
        assertEquals("RIDER-SCOOTER", result.getRider().getRiderId());
        assertEquals(360, result.getSlaCountdownSec());
    }

    @Test
    public void testRequestRefund_AiAutopilotAutoApproval() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST-1");
        customer.setTrustScore(90);
        customer.setWalletBalance(new BigDecimal("10.00"));

        Order order = new Order();
        order.setOrderId(100);
        order.setCustomer(customer);
        order.setTotalAmount(new BigDecimal("15.50"));
        order.setSlaCountdownSec(0);

        when(orderPort.findById(100)).thenReturn(Optional.of(order));

        Map<String, Object> result =
                orderService.requestRefund(
                        100, "The delivery was very late, SLA expired", null, null);

        assertNotNull(result);
        assertEquals("approved", result.get("status"));
        assertTrue(result.get("message").toString().contains("AI-AUTOPILOT"));
        assertEquals(new BigDecimal("25.50"), customer.getWalletBalance());
        verify(ledgerUseCase, times(1))
                .recordTransaction(eq("REFUND-AUTO"), anyString(), anyList());
        verify(hitlQueuePort, times(1)).save(any(HitlQueue.class));
    }

    @Test
    public void testRequestRefund_ManualHitlPath() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST-1");
        customer.setTrustScore(90);
        customer.setWalletBalance(new BigDecimal("10.00"));

        Order order = new Order();
        order.setOrderId(100);
        order.setCustomer(customer);
        order.setTotalAmount(new BigDecimal("15.50"));
        order.setSlaCountdownSec(120);

        when(orderPort.findById(100)).thenReturn(Optional.of(order));

        Map<String, Object> result = orderService.requestRefund(100, "Late delivery", null, null);

        assertNotNull(result);
        assertEquals("pending_admin_approval", result.get("status"));
        assertEquals(new BigDecimal("10.00"), customer.getWalletBalance());
        verify(hitlQueuePort, times(1)).save(any(HitlQueue.class));
    }

    @Test
    public void testCheckout_ValidationErrors() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        orderService.checkout(
                                null,
                                List.of(new OrderUseCase.CartItem("1", 1)),
                                "C",
                                BigDecimal.ZERO,
                                0,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.checkout("C1", null, "C", BigDecimal.ZERO, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> orderService.checkout("C1", List.of(), "C", BigDecimal.ZERO, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        orderService.checkout(
                                "C1",
                                List.of(new OrderUseCase.CartItem("1", 0)),
                                "C",
                                BigDecimal.ZERO,
                                0,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        orderService.checkout(
                                "C1",
                                List.of(
                                        new OrderUseCase.CartItem("1", 1),
                                        new OrderUseCase.CartItem("1", 1)),
                                "C",
                                BigDecimal.ZERO,
                                0,
                                null));
    }

    @Test
    public void testCheckout_InsufficientStock() {
        Customer customer = new Customer();
        customer.setCustomerId("CUST-1");

        DarkStore store = new DarkStore();
        store.setStoreId("Central Store");

        Inventory inventory = new Inventory();
        inventory.setItemId("ITEM-1");
        inventory.setStock(0);
        inventory.setStore(store);

        when(customerPort.findCustomerById("CUST-1")).thenReturn(Optional.of(customer));
        when(inventoryPort.findInventoryById("ITEM-1")).thenReturn(Optional.of(inventory));
        when(darkStorePort.findDarkStoreById("Central Store")).thenReturn(Optional.of(store));
        when(systemConfigPort.getSystemConfig("central_picker_backlog", "0")).thenReturn("0");

        assertThrows(
                IllegalStateException.class,
                () ->
                        orderService.checkout(
                                "CUST-1",
                                List.of(new OrderUseCase.CartItem("ITEM-1", 1)),
                                "C",
                                BigDecimal.ZERO,
                                0,
                                null));
    }

    @Test
    public void testCheckoutFallback() {
        Throwable t = new RuntimeException("DB down");
        assertThrows(
                IllegalStateException.class,
                () ->
                        orderService.checkoutFallback(
                                "C1", List.of(), "C", BigDecimal.ZERO, 0, null, t));
    }

    @Test
    public void testGetCustomerOrders() {
        Order order = new Order();
        order.setOrderId(1);
        when(orderPort.findByCustomerIdOrderByCreatedAtDesc("C1")).thenReturn(List.of(order));

        List<Order> result = orderService.getCustomerOrders("C1");
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getOrderId());
    }

    @Test
    public void testGetOrderById() {
        Order order = new Order();
        order.setOrderId(1);
        when(orderPort.findById(1)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(1);
        assertEquals(1, result.getOrderId());
    }

    @Test
    public void testGetOrderById_NotFound() {
        when(orderPort.findById(99)).thenReturn(Optional.empty());
        assertThrows(java.util.NoSuchElementException.class, () -> orderService.getOrderById(99));
    }
}
