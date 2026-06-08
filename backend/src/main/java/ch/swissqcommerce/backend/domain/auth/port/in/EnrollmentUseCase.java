package ch.swissqcommerce.backend.domain.auth.port.in;

import ch.swissqcommerce.backend.domain.auth.core.model.UserAccount;

public interface EnrollmentUseCase {
    UserAccount register(String email, String password);
}
