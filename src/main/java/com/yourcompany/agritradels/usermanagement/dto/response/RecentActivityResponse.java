package com.yourcompany.agritradels.usermanagement.dto.response;

import com.yourcompany.agritradels.common.model.NotificationType; // Import NotificationType
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RecentActivityResponse {
    private Long id; // ID của sự kiện (ví dụ: orderId, userId, reviewId)
    private NotificationType type; // Loại hoạt động
    private String description; // Mô tả ngắn gọn
    private LocalDateTime timestamp;
    private String link; // Link tới chi tiết (tùy chọn)
}