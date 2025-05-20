package com.yourcompany.agritrade.ordering.domain;

import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "orders",
    uniqueConstraints = {@UniqueConstraint(columnNames = "orderCode")})
@Getter
@Setter
@NoArgsConstructor
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 20)
  private String orderCode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "buyer_id", nullable = false)
  private User buyer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "farmer_id", nullable = false)
  private User farmer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private OrderType orderType; // Tạo Enum này

  // Thông tin giao hàng
  @Column(nullable = false, length = 100)
  private String shippingFullName;

  @Column(nullable = false, length = 20)
  private String shippingPhoneNumber;

  @Column(nullable = false)
  private String shippingAddressDetail;

  @Column(nullable = false, length = 10)
  private String shippingProvinceCode;

  @Column(nullable = false, length = 10)
  private String shippingDistrictCode;

  @Column(nullable = false, length = 10)
  private String shippingWardCode;

  // Giá trị
  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal subTotal;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal shippingFee = BigDecimal.ZERO;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal totalAmount;

  // Thanh toán & Trạng thái
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentMethod paymentMethod = PaymentMethod.COD; // Tạo Enum này

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus paymentStatus = PaymentStatus.PENDING; // Tạo Enum này

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status = OrderStatus.PENDING; // Tạo Enum này

  @Lob private String notes;

  @Column(length = 50)
  private String purchaseOrderNumber; // Cho B2B

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<OrderItem> orderItems = new HashSet<>();

  @OneToMany(
      mappedBy = "order",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<Payment> payments = new HashSet<>(); // Lịch sử thanh toán

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  // Helper methods
  public void addOrderItem(OrderItem item) {
    orderItems.add(item);
    item.setOrder(this);
  }

  public void removeOrderItem(OrderItem item) {
    orderItems.remove(item);
    item.setOrder(null);
  }

  public void addPayment(Payment payment) {
    payments.add(payment);
    payment.setOrder(this);
  }
}
