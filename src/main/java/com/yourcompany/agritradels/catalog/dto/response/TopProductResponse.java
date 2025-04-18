package com.yourcompany.agritradels.catalog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopProductResponse {
    private Long productId;
    private String productName;
    private String productSlug;
    private String thumbnailUrl;
    private Long totalQuantitySold; // Tổng số lượng bán được
    private BigDecimal totalRevenueGenerated; // Tổng doanh thu tạo ra

    // Constructor mới không có thumbnailUrl
    public TopProductResponse(Long productId, String productName, String productSlug, Long totalQuantitySold, BigDecimal totalRevenueGenerated) {
        this.productId = productId;
        this.productName = productName;
        this.productSlug = productSlug;
        this.totalQuantitySold = totalQuantitySold;
        this.totalRevenueGenerated = totalRevenueGenerated;
    }
}