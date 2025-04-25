package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.catalog.dto.response.ProductInfoResponse;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderItemResponse {
    private Long id;
    private Integer quantity;
    private String unit; // Đơn vị tại thời điểm mua
    private BigDecimal pricePerUnit; // Giá tại thời điểm mua
    private BigDecimal totalPrice; // quantity * pricePerUnit
    private ProductInfoResponse product; // Nhúng thông tin cơ bản của sản phẩm
}