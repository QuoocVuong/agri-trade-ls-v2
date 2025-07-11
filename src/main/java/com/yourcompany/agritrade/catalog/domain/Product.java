package com.yourcompany.agritrade.catalog.domain;

import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

@Entity
@Table(
    name = "products",
    uniqueConstraints = {@UniqueConstraint(columnNames = "slug")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ? and version = ?")
@Where(clause = "is_deleted = false")
@Builder
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) // Liên kết với Farmer
  @JoinColumn(name = "farmer_id", nullable = false)
  private User farmer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false) // Liên kết với Category
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true, length = 280)
  private String slug;

  @Lob
  @Column(columnDefinition = "LONGTEXT")
  private String description;

  @Column(nullable = false, length = 50)
  private String unit; // Đơn vị B2C

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal price; // Giá B2C

  @Column(nullable = false)
  private Integer stockQuantity = 0;

  @Version // Annotation @Version
  private Long version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status = ProductStatus.PENDING_APPROVAL;

  @Column(nullable = false, length = 10)
  private String provinceCode;

  @Column(nullable = false)
  private Float averageRating = 0.0f;

  @Column(nullable = false)
  private Integer ratingCount = 0;

  @Column(nullable = false)
  private boolean b2bEnabled = false;

  @Column(nullable = false)
  private Integer favoriteCount = 0;

  @OneToMany(
      mappedBy = "product",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<ProductImage> images = new HashSet<>();

  @Lob // @Lob nếu lý do dài
  private String rejectReason;

  @Column private Integer weightInGrams;
  // bằng gram

  @Column private LocalDate harvestDate; // Ngày thu hoạch/dự kiến

  @Column private LocalDateTime lastStockUpdate; // Tự động cập nhật khi stockQuantity thay đổi

  @Column(nullable = false)
  private boolean negotiablePrice = true; // Mặc định là true

  @Column(length = 50)
  private String wholesaleUnit; // Đơn vị tính cho số lượng lớn (ví dụ: tấn, tạ)

  @Column(precision = 15, scale = 2)
  private BigDecimal referenceWholesalePrice; // Giá tham khảo cho đơn vị sỉ

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(nullable = false)
  private boolean isDeleted = false;
}
