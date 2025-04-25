package com.yourcompany.agritrade.ordering.domain;

import com.yourcompany.agritrade.catalog.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY) // Có thể là LAZY, không cần load product ngay
    @JoinColumn(name = "product_id", nullable = false) // Giữ lại ID sản phẩm
    private Product product; // Liên kết đến sản phẩm gốc

    // Sao chép thông tin sản phẩm tại thời điểm mua
    @Column(nullable = false)
    private String productName;
    @Column(nullable = false, length = 50)
    private String unit;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;
}