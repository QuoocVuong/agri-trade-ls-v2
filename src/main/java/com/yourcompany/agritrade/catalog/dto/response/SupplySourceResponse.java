package com.yourcompany.agritrade.catalog.dto.response;

import com.yourcompany.agritrade.usermanagement.dto.response.FarmerSummaryResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SupplySourceResponse {
    private FarmerSummaryResponse farmerInfo; // Thông tin nông dân
    private Long productId;
    private String productName;
    private String productSlug;
    private String thumbnailUrl; // Ảnh sản phẩm
    private Integer currentStockQuantity; // Số lượng tồn kho hiện tại của nông dân cho sản phẩm này
    private String wholesaleUnit; // Đơn vị tính sỉ
    private BigDecimal referenceWholesalePrice; // Giá sỉ tham khảo
    private LocalDate harvestDate;
    private LocalDateTime lastStockUpdate;
    private boolean negotiablePrice;

}
