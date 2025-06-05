package com.yourcompany.agritrade.notification.dto.response;

import com.yourcompany.agritrade.common.model.NotificationType;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class NotificationResponse {
  private Long id;

  private String message;
  private NotificationType type;
  private boolean isRead;
  private LocalDateTime readAt;
  private String link;
  private LocalDateTime createdAt;
}
