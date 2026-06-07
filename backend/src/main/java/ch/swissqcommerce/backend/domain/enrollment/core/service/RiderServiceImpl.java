package ch.swissqcommerce.backend.domain.enrollment.core.service;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.enrollment.core.model.*;
import ch.swissqcommerce.backend.domain.enrollment.port.in.RiderUseCase;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import ch.swissqcommerce.backend.model.*;

import java.math.BigDecimal;
import java.util.*;

public class RiderServiceImpl implements RiderUseCase {

    private final EnrollmentOutPort outPort;

    public RiderServiceImpl(EnrollmentOutPort outPort) {
        this.outPort = outPort;
    }

    @Override
    public Map<String, Object> submitOnboarding(String name, String vehicleType, String details) {
        String applicationId = "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String riderId = "RDR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        OnboardingApplication application = OnboardingApplication.builder()
                .applicationId(applicationId)
                .applicantType("rider")
                .name(name)
                .details(details != null ? details : "Vehicle: " + vehicleType)
                .build();
        outPort.saveOnboardingApplication(application);

        Rider rider = Rider.builder()
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
    public OrderTelemetryLogEntity recordPing(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp) {
        return outPort.recordTelemetry(orderId, lat, lng, temp);
    }

    @Override
    public Map<String, Object> confirmDelivery(Integer orderId) {
        Order order = outPort.findOrderById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (!"shipping".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalStateException("Order is not in shipping state. Current: " + order.getStatus());
        }

        order.setStatus("delivered");
        outPort.saveOrder(order);

        // Boost rider trust
        Rider rider = order.getRider();
        if (rider != null) {
            int oldTrust = rider.getTrustScore();
            int newTrust = Math.min(100, oldTrust + 5);
            rider.setTrustScore(newTrust);
            outPort.saveRider(rider);

            SecurityTrustLedger riderAudit = SecurityTrustLedger.builder()
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
    public List<Map<String, String>> getAcademyCourses() {
        return List.of(
            Map.of("course_id", "COURSE_001", "course_name", "Cold Chain Logistics Mastery"),
            Map.of("course_id", "COURSE_002", "course_name", "City E-Bike Advanced Maneuvers")
        );
    }

    @Override
    public Map<String, Object> completeAcademyCourse(String riderId, String courseId) {
        Rider rider = outPort.findRiderById(riderId)
                .orElseThrow(() -> new NoSuchElementException("Rider not found: " + riderId));

        String courseName = "COURSE_001".equals(courseId) ? "Cold Chain Logistics Mastery" : "City E-Bike Advanced Maneuvers";

        RiderAcademyCertificate cert = RiderAcademyCertificate.builder()
                .rider(rider)
                .courseName(courseName)
                .build();
        outPort.saveRiderAcademyCertificate(cert);

        int newTrust = Math.min(100, rider.getTrustScore() + 10);
        rider.setTrustScore(newTrust);
        outPort.saveRider(rider);

        SecurityTrustLedger audit = SecurityTrustLedger.builder()
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
}

