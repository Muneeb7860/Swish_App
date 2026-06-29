package ch.swissqcommerce.backend.domain.auth.port.in;

/**
 * Port for MFA operations — OTP generation and verification.
 *
 * <p>Follows the hexagonal boundary (ADR-001): the controller calls this port; the concrete
 * service ({@link ch.swissqcommerce.backend.domain.auth.core.service.MfaService}) lives in core.
 */
public interface MfaUseCase {

    /**
     * Generate a 6-digit OTP, store it in Redis keyed to the session token, and return the session
     * token. The caller (controller) is responsible for delivering the OTP to the user
     * out-of-band (email/SMS).
     *
     * @param userId  the authenticated user's ID
     * @param email   the user's email — used for delivery context
     * @return an opaque session token the client must present at {@link #verifyOtp}
     */
    String initiateOtp(String userId, String email);

    /**
     * Verify the OTP code against the stored value for the given session token.
     *
     * @param sessionToken the token returned by {@link #initiateOtp}
     * @param otpCode      the 6-digit code entered by the user
     * @return the userId if the code is valid and not expired
     * @throws IllegalArgumentException if the code is wrong, expired, or the token is unknown
     */
    String verifyOtp(String sessionToken, String otpCode);
}
