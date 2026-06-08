package ch.swissqcommerce.backend.domain.customer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_profiles", schema = "oltp")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileEntity {

    @Id
    @Column(name = "profile_id", length = 50)
    private String profileId;

    @Column(name = "user_id", length = 50, nullable = false, unique = true)
    private String userId;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Column(name = "default_currency", length = 10, nullable = false)
    private String defaultCurrency;
}
