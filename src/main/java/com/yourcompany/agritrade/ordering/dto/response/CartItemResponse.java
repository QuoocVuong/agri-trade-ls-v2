package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class CartItemResponse {
  private Long id; // ID của cart item
  private Integer quantity;
  private LocalDateTime addedAt;
  private LocalDateTime updatedAt;
  private ProductSummaryResponse product; // Nhúng thông tin tóm tắt sản phẩm
  private BigDecimal
      itemTotal; // Giá trị tạm tính (price * quantity) - sẽ được tính trong mapper/service
}
