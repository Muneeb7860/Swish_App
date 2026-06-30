package ch.swissqcommerce.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ch.swissqcommerce.backend.domain.payment.core.model.Payment;
import ch.swissqcommerce.backend.domain.payment.core.service.PaymentUseCaseImpl;
import ch.swissqcommerce.backend.domain.payment.port.out.OrderValidationPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentEventPublisherPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentLedgerPort;
import ch.swissqcommerce.backend.domain.payment.port.out.PaymentPort;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaymentUseCaseImplTest {

    @Mock private PaymentPort paymentPort;
    @Mock private OrderValidationPort orderValidationPort;
    @Mock private PaymentLedgerPort paymentLedgerPort;
    @Mock private PaymentEventPublisherPort paymentEventPublisherPort;

    @InjectMocks private PaymentUseCaseImpl paymentUseCase;

    @Test
    public void testAuthorizePayment_Success() {
        doNothing().when(orderValidationPort).validateOrderCustomer(1, "C1");

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

        verify(orderValidationPort).validateOrderCustomer(1, "C1");
        verify(paymentLedgerPort).recordPaymentAuth(1, "C1", new BigDecimal("50.00"));
        verify(paymentEventPublisherPort).publishPaymentAuthorized(100, 1, new BigDecimal("50.00"));
        verify(paymentEventPublisherPort)
                .publishPaymentFraudCheck(100, 1, new BigDecimal("50.00"), "C1");
    }

    @Test
    public void testAuthorizePayment_OrderNotFound() {
        doThrow(new IllegalArgumentException("Order not found: 1"))
                .when(orderValidationPort)
                .validateOrderCustomer(1, "C1");

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

        verify(paymentEventPublisherPort).publishPaymentCaptured(100, 1, new BigDecimal("50.00"));
        verify(paymentEventPublisherPort)
                .publishPaymentNotification(100, 1, new BigDecimal("50.00"), "CAPTURED");
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
