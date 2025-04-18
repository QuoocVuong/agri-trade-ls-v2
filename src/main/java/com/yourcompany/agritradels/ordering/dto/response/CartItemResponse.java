package com.yourcompany.agritradels.ordering.dto.response;

import com.yourcompany.agritradels.catalog.dto.response.ProductSummaryResponse; // Import Product Summary
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartItemResponse {
    private Long id; // ID của cart item
    private Integer quantity;
    private LocalDateTime addedAt;
    private LocalDateTime updatedAt;
    private ProductSummaryResponse product; // Nhúng thông tin tóm tắt sản phẩm
    private BigDecimal itemTotal; // Giá trị tạm tính (price * quantity) - sẽ được tính trong mapper/service
}