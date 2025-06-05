package com.yourcompany.agritrade.catalog.dto.response;

import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

// DTO rút gọn cho trang danh sách
@Data
public class ProductSummaryResponse {
  private Long id;
  private String name;
  private CategoryInfoResponse category;
  private String slug;
  private String thumbnailUrl; // URL ảnh đại diện
  private BigDecimal price; // Giá B2C
  private String unit; // Đơn vị B2C
  private Integer stockQuantity;
  private Float averageRating;
  private FarmerInfoResponse farmerInfo; // Thông tin cơ bản của farmer
  private String provinceCode;
  private ProductStatus status; // Có thể cần hiển thị trạng thái
  private LocalDateTime createdAt; // Thêm createdAt nếu chưa có
  private LocalDateTime updatedAt; // *** Thêm updatedAt ***
  private boolean b2bEnabled;
  private String b2bUnit; // Thêm đơn vị B2B
  private BigDecimal b2bBasePrice; // Thêm giá B2B cơ bản
  private Integer minB2bQuantity; // Thêm nếu cần ở Cart
  private List<ProductPricingTierResponse> pricingTiers; // Dùng List
  private boolean isNew;

  // Trong cả ProductDetailResponse và ProductSummaryResponse (hoặc tùy chọn cho Summary)
  private LocalDate harvestDate;
  private LocalDateTime lastStockUpdate;
  private boolean negotiablePrice;
  private String wholesaleUnit;
  private BigDecimal referenceWholesalePrice;
}
