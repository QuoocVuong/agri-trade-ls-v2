package com.yourcompany.agritrade.ordering.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
  private List<CartItemResponse> items;
  private BigDecimal subTotal; // Tổng tiền hàng tạm tính trong giỏ
  private int totalItems; // Tổng số lượng sản phẩm (không phải số loại)
  private List<CartAdjustmentInfo> adjustments;
}
