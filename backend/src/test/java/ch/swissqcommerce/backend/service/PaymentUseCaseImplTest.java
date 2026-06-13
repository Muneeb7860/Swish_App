package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.core.service.PaymentUseCaseImpl;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.port.in.LedgerUseCase;
import ch.swissqcommerce.backend.domain.transaction.port.out.OutboxEventPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.model.OutboxEvent;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class PaymentUseCaseImplTest {

    @Mock private PaymentPort paymentPort;
    @Mock private ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort orderPort;
    @Mock private LedgerUseCase ledgerUseCase;
    @Mock private OutboxEventPort outboxEventPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PaymentUseCaseImpl paymentUseCase;

    @Test
    public void testAuthorizePayment_Success() {
        Customer customer = new Customer();
        customer.setCustomerId("C1");

        Order order = new Order();
        order.setOrderId(1);
        order.setCustomer(customer);

        when(orderPort.findById(1)).thenReturn(Optional.of(order));

        when(paymentPort.save(any(Payment.class)))
                .thenAnswer(
                        i -> {
                            Payment p = i.getArgument(0);
                            p.setPaymentId(100);
                            return p;
                        });

        Payment result =
                paymentUseCase.authorizePayment(1, "C1", new BigDecimal("50.00"), "CARD", null);

        assertNotNull(result);
        assertEquals(100, result.getPaymentId());
        assertEquals("AUTHORIZED", result.getStatus());

        verify(ledgerUseCase).recordTransaction(eq("PAYMENT-AUTH"), anyString(), anyList());
        verify(outboxEventPort, times(2)).save(any(OutboxEvent.class));
        verify(eventPublisher, times(2)).publishEvent(any(OutboxEvent.class));
    }

    @Test
    public void testAuthorizePayment_OrderNotFound() {
        when(orderPort.findById(1)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        paymentUseCase.authorizePayment(
                                1, "C1", new BigDecimal("50.00"), "CARD", null));
    }

    @Test
    public void testAuthorizePayment_ValidationErrors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentUseCase.authorizePayment(null, "C1", BigDecimal.TEN, "CARD", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentUseCase.authorizePayment(1, null, BigDecimal.TEN, "CARD", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentUseCase.authorizePayment(1, "C1", BigDecimal.ZERO, "CARD", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> paymentUseCase.authorizePayment(1, "C1", BigDecimal.TEN, null, null));
    }

    @Test
    public void testCapturePayment_Success() {
        Payment payment = new Payment();
        payment.setPaymentId(100);
        payment.setOrderId(1);
        payment.setAmount(new BigDecimal("50.00"));
        payment.setStatus("AUTHORIZED");

        when(paymentPort.findById(100)).thenReturn(Optional.of(payment));
        when(paymentPort.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentUseCase.capturePayment(100);

        assertNotNull(result);
        assertEquals("CAPTURED", result.getStatus());
        assertNotNull(result.getCapturedAt());

        verify(outboxEventPort, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    public void testCapturePayment_NotAuthorized() {
        Payment payment = new Payment();
        payment.setPaymentId(100);
        payment.setStatus("FAILED");

        when(paymentPort.findById(100)).thenReturn(Optional.of(payment));

        assertThrows(IllegalStateException.class, () -> paymentUseCase.capturePayment(100));
    }
}
