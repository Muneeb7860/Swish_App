package ch.swissqcommerce.backend.domain.enrollment.core.service;

import ch.swissqcommerce.backend.domain.enrollment.core.model.*;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.domain.transaction.core.model.*;
import ch.swissqcommerce.backend.model.*;
import ch.swissqcommerce.backend.model.Customer;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

public class RiderServiceImpl implements RiderUseCase {

    private final EnrollmentOutPort outPort;

    public RiderServiceImpl(EnrollmentOutPort outPort) {
        this.outPort = outPort;
    }

    @Override
    public Map<String, Object> submitOnboarding(String name, String vehicleType, String details) {
        String applicationId = "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String riderId = "RDR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        OnboardingApplication application =
                OnboardingApplication.builder()
                        .applicationId(applicationId)
                        .applicantType("rider")
                        .name(name)
                        .details(details != null ? details : "Vehicle: " + vehicleType)
                        .build();
        outPort.saveOnboardingApplication(application);

        Rider rider =
                Rider.builder()
                        .riderId(riderId)
                        .fullName(name)
                        .vehicleType(vehicleType)
                        .onboardingStatus("pending_review")
                        .build();
        outPort.saveRider(rider);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "submitted");
        result.put("applicationId", applicationId);
        result.put("riderId", riderId);
        result.put("message", "Onboarding application submitted. Awaiting 3-gate approval.");
        return result;
    }

    @Override
    public Map<String, Object> injectCoolant(Integer orderId) {
        outPort.injectDryIce(orderId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "coolant_injected");
        result.put("orderId", orderId);
        result.put("newTemperature", "4.0°C");
        result.put("message", "Dry ice injected. Cargo temperature reset to safe threshold.");
        return result;
    }

    @Override
    public OrderTelemetryLog recordPing(
            Integer orderId,
            BigDecimal lat,
            BigDecimal lng,
            BigDecimal temp,
            String callerRiderId) {
        // Security: verify the authenticated rider is the one assigned to this order.
        // Prevents a malicious rider from submitting temperature data for a competitor's
        // delivery and having it marked as spoiled.
        Order order =
                outPort.findOrderById(orderId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Order not found: " + orderId));
        Rider assignedRider = order.getRider();
        if (assignedRider == null || !callerRiderId.equals(assignedRider.getRiderId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Rider " + callerRiderId + " is not assigned to order " + orderId);
        }
        return outPort.recordTelemetry(orderId, lat, lng, temp);
    }

    @Override
    @CacheEvict(value = "orders", key = "#orderId")
    public Map<String, Object> confirmDelivery(Integer orderId, String pin, String photoUrl) {
        Order order =
                outPort.findOrderById(orderId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Order not found: " + orderId));

        if (!"shipping".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException(
                    "Order is not in shipping state. Current: " + order.getStatus());
        }

        if (order.getDeliveryPin() != null && order.getDeliveryPin().equals(pin)) {
            // PIN matched
        } else if (photoUrl != null && !photoUrl.isBlank()) {
            // PIN mismatched or absent, but proof of delivery photo provided
            order.setProofOfDeliveryPhotoUrl(photoUrl);
        } else {
            throw new IllegalArgumentException(
                    "Invalid Handover: Must provide correct delivery PIN or a Proof of Delivery"
                            + " Photo.");
        }

        order.setStatus("delivered");
        outPort.saveOrder(order);
        outPort.cleanupOrderTelemetry(orderId);

        // Boost rider trust
        Rider rider = order.getRider();
        if (rider != null) {
            int oldTrust = rider.getTrustScore();
            int newTrust = Math.min(100, oldTrust + 5);
            rider.setTrustScore(newTrust);
            outPort.saveRider(rider);

            SecurityTrustLedger riderAudit =
                    SecurityTrustLedger.builder()
                            .actorType("rider")
                            .actorId(rider.getRiderId())
                            .event("DELIVERY-CONFIRMED")
                            .delta(5)
                            .currentValue(newTrust)
                            .build();
            outPort.saveTrustLedger(riderAudit);
        }

        // Boost customer trust and increment consecutive orders
        Customer customer = order.getCustomer();
        if (customer != null) {
            int oldTrust = customer.getTrustScore();
            int newTrust = Math.min(100, oldTrust + 3);
            customer.setTrustScore(newTrust);
            customer.setConsecutiveOrdersCompleted(customer.getConsecutiveOrdersCompleted() + 1);

            // F19: Exit probation after 3 consecutive successful orders
            if (customer.getIsOnProbation() && customer.getConsecutiveOrdersCompleted() >= 3) {
                customer.setIsOnProbation(false);
            }
            outPort.saveCustomer(customer);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "delivered");
        result.put("orderId", orderId);
        result.put("message", "Delivery confirmed. Trust scores updated.");
        return result;
    }

    @Override
    @CacheEvict(value = "orders", key = "#orderId")
    public Map<String, Object> rejectDelivery(
            Integer orderId, String reason, String rejectionPhotoUrl) {
        Order order =
                outPort.findOrderById(orderId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Order not found: " + orderId));

        if (!"shipping".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException(
                    "Order is not in shipping state. Current: " + order.getStatus());
        }

        if (reason == null
                || reason.isBlank()
                || rejectionPhotoUrl == null
                || rejectionPhotoUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid Rejection: Must provide a valid reason and rejection photo.");
        }

        order.setStatus("rejected_at_door");
        order.setRejectionReason(reason);
        order.setRejectionPhotoUrl(rejectionPhotoUrl);
        outPort.saveOrder(order);
        outPort.cleanupOrderTelemetry(orderId);

        // Instant Wallet Refund
        Customer customer = order.getCustomer();
        if (customer != null) {
            customer.setWalletBalance(customer.getWalletBalance().add(order.getTotalAmount()));
            outPort.saveCustomer(customer);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "rejected_at_door");
        result.put("orderId", orderId);
        result.put(
                "message",
                "Delivery rejected at the door. Instant refund issued to customer wallet.");
        return result;
    }

    @Override
    @Cacheable(value = "academy-courses")
    public List<Map<String, String>> getAcademyCourses() {
        return List.of(
                Map.of("course_id", "COURSE_001", "course_name", "Cold Chain Logistics Mastery"),
                Map.of("course_id", "COURSE_002", "course_name", "City E-Bike Advanced Maneuvers"));
    }

    @Override
    public Map<String, Object> completeAcademyCourse(String riderId, String courseId) {
        Rider rider =
                outPort.findRiderById(riderId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Rider not found: " + riderId));

        String courseName =
                "COURSE_001".equals(courseId)
                        ? "Cold Chain Logistics Mastery"
                        : "City E-Bike Advanced Maneuvers";

        RiderAcademyCertificate cert =
                RiderAcademyCertificate.builder().rider(rider).courseName(courseName).build();
        outPort.saveRiderAcademyCertificate(cert);

        int newTrust = Math.min(100, rider.getTrustScore() + 10);
        rider.setTrustScore(newTrust);
        outPort.saveRider(rider);

        SecurityTrustLedger audit =
                SecurityTrustLedger.builder()
                        .actorType("rider")
                        .actorId(riderId)
                        .event("ACADEMY-COURSE-COMPLETED")
                        .delta(10)
                        .currentValue(newTrust)
                        .build();
        outPort.saveTrustLedger(audit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "course_completed");
        result.put("message", "Course completed. Trust score boosted.");
        result.put("new_trust_score", newTrust);
        return result;
    }

    @Override
    public Map<String, Object> approveOnboarding(String applicationId, String gateName) {
        OnboardingApplication application =
                outPort.findOnboardingApplicationById(applicationId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Onboarding application not found: "
                                                        + applicationId));

        if (gateName == null) {
            throw new IllegalArgumentException("Gate name is required");
        }

        switch (gateName.toLowerCase()) {
            case "ops":
                application.setApprovalOps(true);
                break;
            case "compliance":
                if (!application.getApprovalOps()) {
                    throw new IllegalStateException(
                            "Ops approval must precede compliance approval.");
                }
                application.setApprovalCompliance(true);
                break;
            case "admin":
                if (!application.getApprovalOps() || !application.getApprovalCompliance()) {
                    throw new IllegalStateException(
                            "Both ops and compliance approvals required before admin gate.");
                }
                application.setApprovalAdmin(true);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid gate name: "
                                + gateName
                                + ". Must be 'ops', 'compliance', or 'admin'.");
        }

        outPort.saveOnboardingApplication(application);

        boolean fullyApproved =
                application.getApprovalOps()
                        && application.getApprovalCompliance()
                        && application.getApprovalAdmin();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "gate_approved");
        result.put("applicationId", applicationId);
        result.put("gate", gateName.toLowerCase());
        result.put("opsApproved", application.getApprovalOps());
        result.put("complianceApproved", application.getApprovalCompliance());
        result.put("adminApproved", application.getApprovalAdmin());
        result.put("fullyApproved", fullyApproved);

        if (fullyApproved) {
            Optional<Rider> riderOpt = outPort.findRiderByFullName(application.getName());
            if (riderOpt.isPresent()) {
                Rider rider = riderOpt.get();
                rider.setOnboardingStatus("approved");
                outPort.saveRider(rider);
                result.put("status", "fully_approved");
                result.put("riderId", rider.getRiderId());
                result.put("message", "Rider onboarding fully approved and active.");
            } else {
                result.put(
                        "message",
                        "All gates approved, but associated rider profile was not found by name.");
            }
        } else {
            result.put("message", "Gate approved. Awaiting remaining approvals.");
        }

        return result;
    }
}
