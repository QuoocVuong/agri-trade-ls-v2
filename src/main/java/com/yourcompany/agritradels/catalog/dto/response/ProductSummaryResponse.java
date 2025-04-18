package com.yourcompany.agritradels.catalog.dto.response;

import com.yourcompany.agritradels.catalog.domain.ProductStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// DTO rút gọn cho trang danh sách
@Data
public class ProductSummaryResponse {
    private Long id;
    private String name;
    private String slug;
    private String thumbnailUrl; // URL ảnh đại diện
    private BigDecimal price; // Giá B2C
    private String unit; // Đơn vị B2C
    private Float averageRating;
    private FarmerInfoResponse farmerInfo; // Thông tin cơ bản của farmer
    private String provinceCode;
    private ProductStatus status; // Có thể cần hiển thị trạng thái
    private LocalDateTime createdAt; // Thêm createdAt nếu chưa có
    private LocalDateTime updatedAt; // *** Thêm updatedAt ***
    private boolean isB2bAvailable;
}