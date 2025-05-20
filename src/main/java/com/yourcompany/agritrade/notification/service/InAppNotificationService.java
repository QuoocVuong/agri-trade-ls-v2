package com.yourcompany.agritrade.notification.service;

import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.usermanagement.domain.User;

public interface InAppNotificationService {

  /**
   * Tạo và lưu thông báo trong ứng dụng, đồng thời gửi real-time qua WebSocket.
   *
   * @param recipient Người nhận.
   * @param message Nội dung thông báo.
   * @param type Loại thông báo.
   * @param link URL liên kết (có thể null).
   */
  void createAndSendInAppNotification(
      User recipient, String message, NotificationType type, String link);
}
