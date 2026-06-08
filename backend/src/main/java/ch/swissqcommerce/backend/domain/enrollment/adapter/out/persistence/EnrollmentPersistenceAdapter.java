package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.enrollment.core.model.OnboardingApplication;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.OnboardingApplicationEntity;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderEntity;
import ch.swissqcommerce.backend.domain.enrollment.core.model.RiderAcademyCertificate;
import ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence.RiderAcademyCertificateEntity;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.transaction.adapter.out.persistence.OrderEntity;
import ch.swissqcommerce.backend.domain.telemetry.adapter.out.persistence.OrderTelemetryLogEntity;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.domain.transaction.port.out.OrderPort;
import ch.swissqcommerce.backend.repository.SecurityTrustLedgerRepository;
import ch.swissqcommerce.backend.domain.telemetry.port.in.TelemetryUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class EnrollmentPersistenceAdapter implements EnrollmentOutPort {

    @Autowired
    private OnboardingApplicationRepository onboardingRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RiderAcademyCertificateRepository certificateRepository;

    @Autowired
    private OrderPort orderPort;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SecurityTrustLedgerRepository trustLedgerRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private TelemetryUseCase telemetryService;

    private OnboardingApplicationEntity toEntity(OnboardingApplication app) {
        if (app == null) return null;
        return OnboardingApplicationEntity.builder()
                .applicationId(app.getApplicationId())
                .applicantType(app.getApplicantType())
                .name(app.getName())
                .details(app.getDetails())
                .approvalOps(app.getApprovalOps())
                .approvalCompliance(app.getApprovalCompliance())
                .approvalAdmin(app.getApprovalAdmin())
                .createdAt(app.getCreatedAt())
                .build();
    }

    private OnboardingApplication toDomain(OnboardingApplicationEntity entity) {
        if (entity == null) return null;
        return OnboardingApplication.builder()
                .applicationId(entity.getApplicationId())
                .applicantType(entity.getApplicantType())
                .name(entity.getName())
                .details(entity.getDetails())
                .approvalOps(entity.getApprovalOps())
                .approvalCompliance(entity.getApprovalCompliance())
                .approvalAdmin(entity.getApprovalAdmin())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RiderEntity toEntity(Rider rider) {
        if (rider == null) return null;
        return RiderEntity.builder()
                .riderId(rider.getRiderId())
                .fullName(rider.getFullName())
                .vehicleType(rider.getVehicleType())
                .onboardingStatus(rider.getOnboardingStatus())
                .walletBalance(rider.getWalletBalance())
                .activeLat(rider.getActiveLat())
                .activeLng(rider.getActiveLng())
                .trustScore(rider.getTrustScore())
                .cashCollectedLimit(rider.getCashCollectedLimit())
                .currentCashInHand(rider.getCurrentCashInHand())
                .activeShiftId(rider.getActiveShiftId())
                .createdAt(rider.getCreatedAt())
                .build();
    }

    private Rider toDomain(RiderEntity entity) {
        if (entity == null) return null;
        return Rider.builder()
                .riderId(entity.getRiderId())
                .fullName(entity.getFullName())
                .vehicleType(entity.getVehicleType())
                .onboardingStatus(entity.getOnboardingStatus())
                .walletBalance(entity.getWalletBalance())
                .activeLat(entity.getActiveLat())
                .activeLng(entity.getActiveLng())
                .trustScore(entity.getTrustScore())
                .cashCollectedLimit(entity.getCashCollectedLimit())
                .currentCashInHand(entity.getCurrentCashInHand())
                .activeShiftId(entity.getActiveShiftId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private RiderAcademyCertificateEntity toEntity(RiderAcademyCertificate cert) {
        if (cert == null) return null;
        return RiderAcademyCertificateEntity.builder()
                .certificateId(cert.getCertificateId())
                .rider(toEntity(cert.getRider()))
                .courseName(cert.getCourseName())
                .completedAt(cert.getCompletedAt())
                .build();
    }

    private RiderAcademyCertificate toDomain(RiderAcademyCertificateEntity entity) {
        if (entity == null) return null;
        return RiderAcademyCertificate.builder()
                .certificateId(entity.getCertificateId())
                .rider(toDomain(entity.getRider()))
                .courseName(entity.getCourseName())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    @Override
    public void saveOnboardingApplication(OnboardingApplication app) {
        onboardingRepository.save(toEntity(app));
    }

    @Override
    public void saveRider(Rider rider) {
        riderRepository.save(toEntity(rider));
    }

    @Override
    public void saveRiderAcademyCertificate(RiderAcademyCertificate cert) {
        certificateRepository.save(toEntity(cert));
    }

    @Override
    public void saveOrder(Order order) {
        orderPort.save(order);
    }

    @Override
    public void saveCustomer(Customer customer) {
        customerRepository.save(customer);
    }

    @Override
    public void saveTrustLedger(SecurityTrustLedger ledger) {
        trustLedgerRepository.save(ledger);
    }

    @Override
    public Optional<Order> findOrderById(Integer orderId) {
        return orderPort.findById(orderId);
    }

    @Override
    public Optional<Rider> findRiderById(String riderId) {
        return riderRepository.findById(riderId).map(this::toDomain);
    }

    @Override
    public void injectDryIce(Integer orderId) {
        telemetryService.injectDryIce(orderId);
    }

    @Override
    public OrderTelemetryLogEntity recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp) {
        return telemetryService.recordTelemetry(orderId, lat, lng, temp, false);
    }
}

