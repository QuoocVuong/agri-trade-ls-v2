package com.yourcompany.agritrade.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailResponse {
  private Long id;
  private String name;
  private String slug;
  private String description;
  private BigDecimal price; // Giá B2C
  private String unit; // Đơn vị B2C
  private Integer stockQuantity;
  private Float averageRating;
  private Integer ratingCount;
  private Integer favoriteCount;
  private ProductStatus status;
  private String rejectReason; //  do từ chối
  private String provinceCode;

  // Thông tin liên kết
  private CategoryInfoResponse category;
  private FarmerInfoResponse farmer;
  private List<ProductImageResponse> images; // Dùng List để có thứ tự

  // Thông tin B2B
  private boolean b2bEnabled;
  private LocalDate harvestDate;
  private LocalDateTime lastStockUpdate;
  private boolean negotiablePrice;
  private String wholesaleUnit;
  private BigDecimal referenceWholesalePrice;

  private List<ReviewResponse> reviews;

  // Thêm trường mới cho sản phẩm liên quan
  private List<ProductSummaryResponse>
      relatedProducts; // Dùng Summary DTO để tránh quá nhiều thông tin

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
