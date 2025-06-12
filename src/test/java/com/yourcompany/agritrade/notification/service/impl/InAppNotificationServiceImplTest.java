package com.yourcompany.agritrade.notification.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.notification.domain.Notification;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.notification.mapper.NotificationMapper;
import com.yourcompany.agritrade.notification.repository.NotificationRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceImplTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private NotificationMapper notificationMapper;

  @InjectMocks private InAppNotificationServiceImpl inAppNotificationService;

  private User testRecipient;
  private Notification savedNotification;
  private NotificationResponse notificationResponseDto;

  @BeforeEach
  void setUp() {
    testRecipient = new User();
    testRecipient.setId(1L);
    testRecipient.setEmail("recipient@example.com");
    testRecipient.setFullName("Test Recipient");

    // Giả lập đối tượng Notification sau khi được lưu
    savedNotification = new Notification();
    savedNotification.setId(100L);
    savedNotification.setRecipient(testRecipient);
    savedNotification.setMessage("Test message");
    savedNotification.setType(NotificationType.OTHER);
    savedNotification.setLink("/test-link");

    // Giả lập đối tượng NotificationResponse sau khi map
    notificationResponseDto = new NotificationResponse();
    notificationResponseDto.setId(100L);
    notificationResponseDto.setMessage("Test message");
    notificationResponseDto.setType(NotificationType.OTHER);
    notificationResponseDto.setLink("/test-link");
  }

  @Test
  @DisplayName("Create and Send In-App Notification - Success")
  void createAndSendInAppNotification_whenValidInput_shouldSaveAndSendViaWebSocket() {
    // Arrange
    String message = "Your order has been shipped!";
    NotificationType type = NotificationType.ORDER_STATUS_UPDATE;
    String link = "/orders/123";

    when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
    when(notificationMapper.toNotificationResponse(savedNotification))
        .thenReturn(notificationResponseDto);
    doNothing()
        .when(messagingTemplate)
        .convertAndSend(anyString(), any(NotificationResponse.class));

    // Act
    inAppNotificationService.createAndSendInAppNotification(testRecipient, message, type, link);

    // Assert
    ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(notificationCaptor.capture());
    Notification capturedNotification = notificationCaptor.getValue();
    assertEquals(testRecipient, capturedNotification.getRecipient());
    assertEquals(message, capturedNotification.getMessage());
    assertEquals(type, capturedNotification.getType());
    assertEquals(link, capturedNotification.getLink());

    verify(notificationMapper).toNotificationResponse(savedNotification);

    String expectedDestination = "/user/" + testRecipient.getEmail() + "/queue/notifications";
    verify(messagingTemplate).convertAndSend(eq(expectedDestination), eq(notificationResponseDto));
  }

  @Test
  @DisplayName("Create and Send In-App Notification - Recipient is Null")
  void createAndSendInAppNotification_whenRecipientIsNull_shouldLogWarningAndReturn() {
    // Arrange
    String message = "Test message";
    NotificationType type = NotificationType.OTHER;
    String link = "/link";

    // Act
    inAppNotificationService.createAndSendInAppNotification(null, message, type, link);

    // Assert
    verify(notificationRepository, never()).save(any(Notification.class));
    verify(notificationMapper, never()).toNotificationResponse(any(Notification.class));
    verify(messagingTemplate, never()).convertAndSend(anyString(), any(NotificationResponse.class));
    // Kiểm tra log (khó hơn trong unit test thuần túy, có thể cần thư viện hỗ trợ log testing)
    // Hoặc bạn có thể tin tưởng vào việc log đã được ghi dựa trên code.
  }

  @Test
  @DisplayName("Create and Send In-App Notification - Repository Save Fails")
  void createAndSendInAppNotification_whenRepositorySaveFails_shouldLogErrorAndNotSendWebSocket() {
    // Arrange
    String message = "Test message";
    NotificationType type = NotificationType.OTHER;
    String link = "/link";
    RuntimeException dbException = new RuntimeException("Database save error");

    when(notificationRepository.save(any(Notification.class))).thenThrow(dbException);

    // Act
    inAppNotificationService.createAndSendInAppNotification(testRecipient, message, type, link);

    // Assert
    verify(notificationRepository).save(any(Notification.class));
    verify(notificationMapper, never()).toNotificationResponse(any(Notification.class));
    verify(messagingTemplate, never()).convertAndSend(anyString(), any(NotificationResponse.class));
    // Kiểm tra log lỗi
  }

  @Test
  @DisplayName("Create and Send In-App Notification - WebSocket Send Fails")
  void createAndSendInAppNotification_whenWebSocketSendFails_shouldLogError() {
    // Arrange
    String message = "Test message";
    NotificationType type = NotificationType.OTHER;
    String link = "/link";
    RuntimeException wsException = new RuntimeException("WebSocket send error");

    when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);
    when(notificationMapper.toNotificationResponse(savedNotification))
        .thenReturn(notificationResponseDto);
    doThrow(wsException)
        .when(messagingTemplate)
        .convertAndSend(anyString(), any(NotificationResponse.class));

    // Act
    inAppNotificationService.createAndSendInAppNotification(testRecipient, message, type, link);

    // Assert
    verify(notificationRepository).save(any(Notification.class));
    verify(notificationMapper).toNotificationResponse(savedNotification);
    String expectedDestination = "/user/" + testRecipient.getEmail() + "/queue/notifications";
    verify(messagingTemplate).convertAndSend(eq(expectedDestination), eq(notificationResponseDto));
    // Kiểm tra log lỗi
  }
}
