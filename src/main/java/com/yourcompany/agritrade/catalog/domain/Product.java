package com.yourcompany.agritrade.catalog.domain;

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
@SQLDelete(sql = "UPDATE products SET is_deleted = true WHERE id = ? and version = ?")
@Where(clause = "is_deleted = false")
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
  @Column(columnDefinition = "LONGTEXT") // Đảm bảo khớp với migration
  private String description;

  @Column(nullable = false, length = 50)
  private String unit; // Đơn vị B2C

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal price; // Giá B2C

  @Column(nullable = false)
  private Integer stockQuantity = 0;

  @Version // *** Thêm Annotation @Version ***
  private Long version; // Kiểu dữ liệu có thể là Long, Integer, Timestamp...

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductStatus status = ProductStatus.DRAFT; // Tạo Enum này

  @Column(nullable = false, length = 10)
  private String provinceCode;

  @Column(nullable = false)
  private Float averageRating = 0.0f;

  @Column(nullable = false)
  private Integer ratingCount = 0;

  @Column(nullable = false)
  private boolean b2bEnabled = false;

  @Column(length = 50)
  private String b2bUnit;

  private Integer minB2bQuantity = 1;

  @Column(precision = 15, scale = 2)
  private BigDecimal b2bBasePrice;

  @Column(nullable = false)
  private Integer favoriteCount = 0;

  @OneToMany(
      mappedBy = "product",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<ProductImage> images = new HashSet<>();

  @OneToMany(
      mappedBy = "product",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private Set<ProductPricingTier> pricingTiers = new HashSet<>(); // Nếu dùng giá bậc thang

  @Lob // Có thể cần @Lob nếu lý do dài
  private String rejectReason; // Thêm trường lý do từ chối

  @Column // Có thể nullable nếu không phải SP nào cũng có cân nặng
  private Integer weightInGrams; // Trọng lượng của 1 'unit' (đơn vị B2C) tính bằng gram

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(nullable = false)
  private boolean isDeleted = false;

  // Helper methods cho images/pricingTiers (tùy chọn)
  public void addImage(ProductImage image) {
    images.add(image);
    image.setProduct(this);
  }

  public void removeImage(ProductImage image) {
    images.remove(image);
    image.setProduct(null);
  }

  public void addPricingTier(ProductPricingTier tier) {
    pricingTiers.add(tier);
    tier.setProduct(this);
  }

  public void removePricingTier(ProductPricingTier tier) {
    pricingTiers.remove(tier);
    tier.setProduct(null);
  }
}
