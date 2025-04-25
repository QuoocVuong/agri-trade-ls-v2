package com.yourcompany.agritrade.ordering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(columnNames = "transactionCode") // Mã giao dịch cổng thanh toán phải là duy nhất (nếu có)
})
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(unique = true, length = 100)
    private String transactionCode; // Mã từ cổng thanh toán

    @Column(nullable = false, length = 50)
    private String paymentGateway; // Tên cổng/phương thức (COD, VNPAY...)

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentTransactionStatus status = PaymentTransactionStatus.PENDING; // Tạo Enum này

    private LocalDateTime paymentTime; // Thời gian thanh toán thành công

    @Lob
    private String gatewayMessage; // Thông tin/lỗi từ cổng thanh toán

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}