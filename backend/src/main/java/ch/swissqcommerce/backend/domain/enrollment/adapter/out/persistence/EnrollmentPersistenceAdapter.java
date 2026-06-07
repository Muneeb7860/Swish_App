package ch.swissqcommerce.backend.domain.enrollment.adapter.out.persistence;

import ch.swissqcommerce.backend.domain.transaction.core.model.*;

import ch.swissqcommerce.backend.domain.enrollment.core.model.OnboardingApplication;
import ch.swissqcommerce.backend.domain.enrollment.core.model.Rider;
import ch.swissqcommerce.backend.domain.enrollment.core.model.RiderAcademyCertificate;
import ch.swissqcommerce.backend.domain.enrollment.port.out.EnrollmentOutPort;
import ch.swissqcommerce.backend.model.Customer;
import ch.swissqcommerce.backend.domain.transaction.core.model.Order;
import ch.swissqcommerce.backend.domain.telemetry.core.model.OrderTelemetryLog;
import ch.swissqcommerce.backend.model.SecurityTrustLedger;
import ch.swissqcommerce.backend.repository.CustomerRepository;
import ch.swissqcommerce.backend.repository.OrderRepository;
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
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SecurityTrustLedgerRepository trustLedgerRepository;

    @Autowired
    private TelemetryUseCase telemetryService;

    @Override
    public void saveOnboardingApplication(OnboardingApplication app) {
        onboardingRepository.save(app);
    }

    @Override
    public void saveRider(Rider rider) {
        riderRepository.save(rider);
    }

    @Override
    public void saveRiderAcademyCertificate(RiderAcademyCertificate cert) {
        certificateRepository.save(cert);
    }

    @Override
    public void saveOrder(Order order) {
        orderRepository.save(order);
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
        return orderRepository.findById(orderId);
    }

    @Override
    public Optional<Rider> findRiderById(String riderId) {
        return riderRepository.findById(riderId);
    }

    @Override
    public Optional<OnboardingApplication> findOnboardingApplicationById(String applicationId) {
        return onboardingRepository.findById(applicationId);
    }

    @Override
    public Optional<Rider> findRiderByFullName(String fullName) {
        return riderRepository.findByFullName(fullName);
    }

    @Override
    public void injectDryIce(Integer orderId) {
        telemetryService.injectDryIce(orderId);
    }

    @Override
    public OrderTelemetryLog recordTelemetry(Integer orderId, BigDecimal lat, BigDecimal lng, BigDecimal temp) {
        return telemetryService.recordTelemetry(orderId, lat, lng, temp, false);
    }
}

