package com.yourcompany.agritrade.catalog.dto.request;

import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class ProductRequest {
  @NotBlank(message = "Product name is required")
  @Size(max = 255)
  private String name;

  @Size(max = 255)
  private String slug;

  @NotNull(message = "Category ID is required")
  private Integer categoryId;

  private String description;

  @NotBlank(message = "Unit is required")
  @Size(max = 50)
  private String unit; // B2C unit

  @NotNull(message = "Price is required")
  @PositiveOrZero(message = "Price must be non-negative")
  private BigDecimal price; // B2C price

  @NotNull(message = "Stock quantity is required")
  @PositiveOrZero(message = "Stock quantity must be non-negative")
  private Integer stockQuantity;

  //  PENDING_APPROVAL ban đầu
  private ProductStatus status = ProductStatus.PENDING_APPROVAL;

  private boolean b2bEnabled = false;

  private LocalDate harvestDate;

  private Boolean negotiablePrice;
  private String wholesaleUnit;
  private BigDecimal referenceWholesalePrice;

  @Valid private List<ProductImageRequest> images;
}
