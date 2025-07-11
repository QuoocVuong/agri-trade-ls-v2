package com.yourcompany.agritrade.interaction.domain;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "consumer_id", nullable = false)
  private User consumer; // Người viết review

  @ManyToOne(fetch = FetchType.LAZY) // Có thể null nếu không liên kết đơn hàng
  @JoinColumn(name = "order_id")
  private Order order; // Đơn hàng đã mua sản phẩm này

  @Min(1)
  @Max(5) // Ràng buộc giá trị rating
  @Column(nullable = false)
  private int rating; // Điểm (1-5 sao)

  @Lob
  @Column(columnDefinition = "TEXT")
  private String comment; // Nội dung bình luận

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReviewStatus status = ReviewStatus.PENDING; // Tạo Enum này

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;
}
