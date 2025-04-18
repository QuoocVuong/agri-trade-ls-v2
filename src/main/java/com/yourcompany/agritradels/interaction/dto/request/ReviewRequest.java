package com.yourcompany.agritradels.interaction.dto.request;

import jakarta.validation.constraints.*; // Import các constraints
import lombok.Data;

@Data
public class ReviewRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    // Order ID có thể tùy chọn, không bắt buộc phải liên kết với đơn hàng cụ thể
    private Long orderId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer rating; // Dùng Integer để check null

    @Size(max = 1000, message = "Comment is too long")
    private String comment; // Bình luận có thể không bắt buộc
}