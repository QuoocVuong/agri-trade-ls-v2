package com.yourcompany.agritrade.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartAdjustmentInfo {
  private Long productId;
  private String productName;
  private String
      message; // Ví dụ: "Số lượng đã được cập nhật thành X do thay đổi tồn kho." hoặc "Sản phẩm đã
  // hết hàng và được xóa."
  private String type; // Ví dụ: "ADJUSTED", "REMOVED"
}
