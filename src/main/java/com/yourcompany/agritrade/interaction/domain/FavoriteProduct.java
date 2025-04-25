package com.yourcompany.agritrade.interaction.domain;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "favorite_products")
@Getter
@Setter
@NoArgsConstructor
@IdClass(FavoriteProduct.FavoriteProductId.class) // Khóa chính phức hợp
public class FavoriteProduct {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;

    // Lớp IdClass
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FavoriteProductId implements Serializable {
        private Long user;
        private Long product;

        public FavoriteProductId(Long userId, Long productId) {
            this.user = userId;
            this.product = productId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FavoriteProductId that = (FavoriteProductId) o;
            return Objects.equals(user, that.user) && Objects.equals(product, that.product);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, product);
        }
    }
}