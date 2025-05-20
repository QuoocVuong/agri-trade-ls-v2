package com.yourcompany.agritrade.catalog.dto.response;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductPricingTierResponse {
  private Long id;
  private Integer minQuantity;
  private BigDecimal pricePerUnit;
}
