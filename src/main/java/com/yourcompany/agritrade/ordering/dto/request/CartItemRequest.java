package com.yourcompany.agritrade.ordering.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartItemRequest {
  @NotNull(message = "Product ID is required")
  private Long productId;

  @NotNull(message = "Quantity is required")
  @Min(value = 1, message = "Quantity must be at least 1")
  private Integer quantity = 1; // Mặc định là 1
}
