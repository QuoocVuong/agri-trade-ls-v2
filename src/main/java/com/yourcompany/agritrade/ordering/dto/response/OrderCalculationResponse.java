package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.OrderType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCalculationResponse {
  private BigDecimal subTotal;
  private BigDecimal shippingFee;
  private BigDecimal discountAmount;
  private BigDecimal totalAmount;
  private OrderType calculatedOrderType; // Trả về cả loại đơn hàng đã xác định
  // Có thể thêm chi tiết item với giá đã tính
  // private List<CalculatedOrderItem> items;
}
