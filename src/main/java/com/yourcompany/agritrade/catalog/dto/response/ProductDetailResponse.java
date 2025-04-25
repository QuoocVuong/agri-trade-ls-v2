package com.yourcompany.agritrade.catalog.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List; // Đổi sang List cho thứ tự ảnh


// DTO đầy đủ cho trang chi tiết
@Data
// @EqualsAndHashCode(callSuper = true) // Có thể kế thừa từ Summary nếu muốn
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDetailResponse { // Hoặc kế thừa ProductSummaryResponse
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price; // Giá B2C
    private String unit; // Đơn vị B2C
    private Integer stockQuantity;
    private Float averageRating;
    private Integer ratingCount;
    private Integer favoriteCount;
    private ProductStatus status;
    private String rejectReason; // Thêm lý do từ chối
    private String provinceCode;

    // Thông tin liên kết
    private CategoryInfoResponse category;
    private FarmerInfoResponse farmer;
    private List<ProductImageResponse> images; // Dùng List để có thứ tự

    // Thông tin B2B
    private boolean isB2bAvailable;
    private String b2bUnit;
    private Integer minB2bQuantity;
    private BigDecimal b2bBasePrice;
    private List<ProductPricingTierResponse> pricingTiers; // Dùng List

    // Thêm trường mới cho sản phẩm liên quan
    private List<ProductSummaryResponse> relatedProducts; // Dùng Summary DTO để tránh quá nhiều thông tin

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}