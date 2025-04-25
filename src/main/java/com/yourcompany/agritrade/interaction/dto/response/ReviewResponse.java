package com.yourcompany.agritrade.interaction.dto.response;

import com.yourcompany.agritrade.common.model.ReviewStatus; // Import Enum
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse; // Import DTO user đơn giản
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ReviewResponse {
    private Long id;
    private Long productId; // Chỉ cần ID sản phẩm
    private Long orderId; // ID đơn hàng (nếu có)
    private UserInfoSimpleResponse consumer; // Thông tin người đánh giá
    private int rating;
    private String comment;
    private ReviewStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}