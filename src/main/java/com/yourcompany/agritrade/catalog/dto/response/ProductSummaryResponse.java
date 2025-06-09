package com.yourcompany.agritrade.catalog.dto.response;

import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;


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
  private ProductStatus status; // hiển thị trạng thái
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean b2bEnabled;
  private boolean isNew;


  private LocalDate harvestDate;
  private LocalDateTime lastStockUpdate;
  private boolean negotiablePrice;
  private String wholesaleUnit;
  private BigDecimal referenceWholesalePrice;
}
