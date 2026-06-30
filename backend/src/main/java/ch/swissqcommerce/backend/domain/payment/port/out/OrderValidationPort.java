package ch.swissqcommerce.backend.domain.payment.port.out;

/** Outbound port to decouple order validation from the payment core domain. */
public interface OrderValidationPort {
    void validateOrderCustomer(Integer orderId, String customerId);
}
