package com.yourcompany.agritrade.notification.service.impl;

import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.notification.domain.Notification;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.notification.mapper.NotificationMapper;
import com.yourcompany.agritrade.notification.repository.NotificationRepository;
import com.yourcompany.agritrade.notification.service.InAppNotificationService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationServiceImpl implements InAppNotificationService {

  private final NotificationRepository notificationRepository;
  private final SimpMessagingTemplate messagingTemplate; // Inject WebSocket template
  private final NotificationMapper notificationMapper; // Inject Mapper

  @Override
  @Transactional // Lưu DB nên cần transactional
  @Async("taskExecutor") // Chạy bất đồng bộ
  public void createAndSendInAppNotification(
      User recipient, String message, NotificationType type, String link) {
    if (recipient == null) {
      log.warn("Cannot send in-app notification: Recipient is null. Message: {}", message);
      return;
    }

    try {
      // 1. Tạo và lưu Notification vào DB
      Notification notification = new Notification(recipient, message, type, link);
      Notification savedNotification = notificationRepository.save(notification);
      log.info(
          "Saved in-app notification {} for user {}", savedNotification.getId(), recipient.getId());

      // 2. Chuẩn bị DTO để gửi qua WebSocket
      NotificationResponse notificationDto =
          notificationMapper.toNotificationResponse(savedNotification);

      // 3. Gửi thông báo real-time qua WebSocket đến private queue của người nhận
      String destination =
          "/user/" + recipient.getEmail() + "/queue/notifications"; // Queue riêng cho notification
      messagingTemplate.convertAndSend(destination, notificationDto);
      log.info("Sent WebSocket notification to {}: {}", destination, notificationDto.getId());

    } catch (Exception e) {
      log.error(
          "Failed to create or send in-app notification for user {}: {}",
          recipient.getId(),
          e.getMessage(),
          e);
      // Có thể thêm cơ chế retry hoặc ghi vào hàng đợi lỗi
    }
  }
}
