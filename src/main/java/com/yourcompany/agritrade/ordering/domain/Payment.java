package com.yourcompany.agritrade.ordering.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = "transactionCode")
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
  private String paymentGateway; // Tên cổng/phương thức

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentTransactionStatus status = PaymentTransactionStatus.PENDING;

  private LocalDateTime paymentTime; // Thời gian thanh toán thành công

  @Lob private String gatewayMessage; // Thông tin/lỗi từ cổng thanh toán

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
