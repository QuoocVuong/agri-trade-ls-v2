package com.yourcompany.agritrade.catalog.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductPricingTierResponse {
    private Long id;
    private Integer minQuantity;
    private BigDecimal pricePerUnit;
}