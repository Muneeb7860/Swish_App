package ch.swissqcommerce.backend.domain.auth.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import lombok.Getter;

@Getter
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    private String id;
    private EmailAddress emailAddress;
    private PasswordHash passwordHash;
    private AccountStatus status;
    /** JWT role claim — "CUSTOMER", "ADMIN", "RIDER", or "WHOLESALER". */
    @Builder.Default
    private String role = "CUSTOMER";

    public void lockAccount() {
        this.status = AccountStatus.LOCKED;
    }

    public void activateAccount() {
        this.status = AccountStatus.ACTIVE;
    }
}
