package com.yourcompany.agritrade.usermanagement.dto.response;

import com.yourcompany.agritrade.common.model.NotificationType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecentActivityResponse {
  private Long id; // ID của sự kiện (ví dụ: orderId, userId, reviewId)
  private NotificationType type; // Loại hoạt động
  private String description; // Mô tả ngắn gọn
  private LocalDateTime timestamp;
  private String link; // Link tới chi tiết (tùy chọn)
}
