package com.yourcompany.agritrade.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductPricingTierRequest {
  @NotNull(message = "Minimum quantity is required")
  @Min(value = 1, message = "Minimum quantity must be at least 1")
  private Integer minQuantity;

  @NotNull(message = "Price per unit is required")
  private BigDecimal pricePerUnit;
}
