package com.yourcompany.agritrade.interaction.dto.response;

import com.yourcompany.agritrade.catalog.dto.response.ProductInfoResponse;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReviewResponse {
  private Long id;
  // private Long productId; // Chỉ cần ID sản phẩm
  private Long orderId; // ID đơn hàng (nếu có)
  private UserInfoSimpleResponse consumer; // Thông tin người đánh giá
  private int rating;
  private String comment;
  private ProductInfoResponse productInfo;
  private ReviewStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
