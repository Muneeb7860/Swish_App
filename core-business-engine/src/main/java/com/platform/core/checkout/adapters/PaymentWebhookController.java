package com.platform.core.checkout.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody MockStripeWebhookEvent event,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        log.info("Received Stripe Webhook: type={}, paymentIntentId={}", event.type(), event.paymentIntentId());

        // Validate webhook signature here in production
        
        switch (event.type()) {
            case "payment_intent.succeeded":
                log.info("PaymentIntent {} succeeded. Finalizing order.", event.paymentIntentId());
                // Call paymentService.confirmPayment()
                break;
            case "payment_intent.payment_failed":
                log.warn("PaymentIntent {} failed.", event.paymentIntentId());
                // Call paymentService.failPayment()
                break;
            default:
                log.debug("Unhandled event type: {}", event.type());
        }

        return ResponseEntity.ok("Webhook Received");
    }
}

record MockStripeWebhookEvent(String id, String type, String paymentIntentId) {}
