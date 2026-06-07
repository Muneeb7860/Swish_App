package ch.swissqcommerce.backend.domain.feedback.core.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "feedbacks", schema = "oltp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "rider_rating", nullable = false)
    private Integer riderRating;

    @Column(name = "store_rating", nullable = false)
    private Integer storeRating;

    @Column(name = "product_rating", nullable = false)
    private Integer productRating;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
