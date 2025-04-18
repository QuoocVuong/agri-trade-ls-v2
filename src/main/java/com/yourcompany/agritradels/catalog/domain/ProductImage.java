package com.yourcompany.agritradels.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 512)
    private String imageUrl;

    @Column(nullable = false)
    private boolean isDefault = false;

    @Column(nullable = false)
    private int displayOrder = 0; // Thêm trường thứ tự

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructor cập nhật
    public ProductImage(String imageUrl, boolean isDefault, int displayOrder) {
        this.imageUrl = imageUrl;
        this.isDefault = isDefault;
        this.displayOrder = displayOrder;
    }
}