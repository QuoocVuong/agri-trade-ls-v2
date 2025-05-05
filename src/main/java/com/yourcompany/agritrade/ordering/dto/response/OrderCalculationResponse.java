package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCalculationResponse {
    private BigDecimal subTotal;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private OrderType calculatedOrderType; // Trả về cả loại đơn hàng đã xác định
    // Có thể thêm chi tiết item với giá đã tính nếu cần
     //private List<CalculatedOrderItem> items;
}
