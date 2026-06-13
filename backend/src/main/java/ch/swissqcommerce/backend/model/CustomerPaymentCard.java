package ch.swissqcommerce.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "customer_payment_cards", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPaymentCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Integer cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    @JsonIgnore
    private Customer customer;

    @Column(name = "card_type", length = 20, nullable = false)
    @NotBlank
    @Size(max = 20)
    private String cardType;

    @Column(name = "last_four_digits", length = 4, nullable = false)
    @NotBlank
    @Pattern(regexp = "^\\d{4}$")
    private String lastFourDigits;

    @Column(name = "token_reference", length = 100, unique = true, nullable = false)
    @NotBlank
    @Size(max = 100)
    private String tokenReference;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
