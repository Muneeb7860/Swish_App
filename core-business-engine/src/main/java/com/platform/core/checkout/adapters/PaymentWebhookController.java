package com.platform.core.checkout.adapters;

import com.platform.core.checkout.domain.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Fix #19: Webhook controller now actually calls PaymentService methods
 * instead of leaving them as comments.
 */
@RestController
@RequestMapping("/api/webhooks/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);
    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody MockStripeWebhookEvent event,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        log.info("Received Stripe Webhook: type={}, paymentIntentId={}", event.type(), event.paymentIntentId());

        // TODO: Validate webhook signature in production (Stripe.Webhook.constructEvent)

        switch (event.type()) {
            case "payment_intent.succeeded":
                log.info("PaymentIntent {} succeeded. Finalizing order.", event.paymentIntentId());
                paymentService.confirmPayment(event.paymentIntentId());
                break;
            case "payment_intent.payment_failed":
                log.warn("PaymentIntent {} failed.", event.paymentIntentId());
                paymentService.failPayment(event.paymentIntentId());
                break;
            default:
                log.debug("Unhandled event type: {}", event.type());
        }

        return ResponseEntity.ok("Webhook Received");
    }
}

record MockStripeWebhookEvent(String id, String type, String paymentIntentId) {}
