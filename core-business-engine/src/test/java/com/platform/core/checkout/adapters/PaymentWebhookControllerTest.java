package com.platform.core.checkout.adapters;

import com.platform.core.checkout.domain.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class PaymentWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentWebhookController controller;

    @Test
    public void testHandleStripeWebhook_Succeeded() throws Exception {
        // We use Reflection since MockStripeWebhookEvent is a package-private record in the controller file.
        // Actually it's defined at the bottom of the file as record MockStripeWebhookEvent(String id, String type, String paymentIntentId) {}
        // We can just construct it directly if it's in the same package.
        Object event = Class.forName("com.platform.core.checkout.adapters.MockStripeWebhookEvent")
                .getDeclaredConstructors()[0]
                .newInstance("evt_1", "payment_intent.succeeded", "pi_123");

        ResponseEntity<String> response = (ResponseEntity<String>) controller.getClass()
                .getMethod("handleStripeWebhook", Class.forName("com.platform.core.checkout.adapters.MockStripeWebhookEvent"), String.class)
                .invoke(controller, event, "sig_123");

        assertEquals("Webhook Received", response.getBody());
        verify(paymentService).confirmPayment("pi_123");
    }

    @Test
    public void testHandleStripeWebhook_Failed() throws Exception {
        Object event = Class.forName("com.platform.core.checkout.adapters.MockStripeWebhookEvent")
                .getDeclaredConstructors()[0]
                .newInstance("evt_2", "payment_intent.payment_failed", "pi_456");

        ResponseEntity<String> response = (ResponseEntity<String>) controller.getClass()
                .getMethod("handleStripeWebhook", Class.forName("com.platform.core.checkout.adapters.MockStripeWebhookEvent"), String.class)
                .invoke(controller, event, "sig_456");

        assertEquals("Webhook Received", response.getBody());
        verify(paymentService).failPayment("pi_456");
    }

    @Test
    public void testHandleStripeWebhook_Unhandled() throws Exception {
        Object event = Class.forName("com.platform.core.checkout.adapters.MockStripeWebhookEvent")
                .getDeclaredConstructors()[0]
                .newInstance("evt_3", "other_event", "pi_789");

        ResponseEntity<String> response = (ResponseEntity<String>) controller.getClass()
                .getMethod("handleStripeWebhook", Class.forName("com.platform.core.checkout.adapters.MockStripeWebhookEvent"), String.class)
                .invoke(controller, event, "sig_789");

        assertEquals("Webhook Received", response.getBody());
        verifyNoInteractions(paymentService);
    }
}
