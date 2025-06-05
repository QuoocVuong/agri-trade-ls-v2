package com.yourcompany.agritrade.ordering.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(
      fetch = FetchType.LAZY,
      optional = false) // Mỗi đơn hàng có 1 hóa đơn (hoặc OneToMany nếu cần nhiều)
  @JoinColumn(name = "order_id", nullable = false, unique = true)
  private Order order;

  @Column(nullable = false, unique = true, length = 30)
  private String invoiceNumber; // Mã hóa đơn (ví dụ: INV-LS250419-8761)

  @Column(nullable = false)
  private LocalDate issueDate; // Ngày xuất hóa đơn

  private LocalDate dueDate; // Hạn thanh toán (cho B2B Invoice)

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal totalAmount; // Tổng tiền trên hóa đơn (thường bằng order.totalAmount)

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private InvoiceStatus status =
      InvoiceStatus.DRAFT; // Trạng thái hóa đơn (Draft, Issued, Paid, Void)

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
