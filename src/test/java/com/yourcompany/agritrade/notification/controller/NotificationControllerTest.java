package com.yourcompany.agritrade.notification.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import(TestSecurityConfig.class)
@WithMockUser // Tất cả API trong controller này yêu cầu xác thực
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private NotificationService notificationService;

  // Authentication sẽ được cung cấp bởi @WithMockUser

  private NotificationResponse notificationResponse;
  private Page<NotificationResponse> notificationResponsePage;

  @BeforeEach
  void setUp() {
    notificationResponse = new NotificationResponse();
    notificationResponse.setId(1L);
    notificationResponse.setMessage("Thông báo thử nghiệm");
    notificationResponse.setCreatedAt(LocalDateTime.now());

    notificationResponsePage = new PageImpl<>(List.of(notificationResponse));
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Thông báo")
  class GetNotificationsTests {
    @Test
    @DisplayName("GET /api/notifications/my - Lấy Thông báo của Tôi - Thành công")
    void getMyNotifications_success() throws Exception {
      when(notificationService.getMyNotifications(any(Authentication.class), any(Pageable.class)))
          .thenReturn(notificationResponsePage);

      mockMvc
          .perform(get("/api/notifications/my").param("page", "0").param("size", "15"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].message", is(notificationResponse.getMessage())));
    }

    @Test
    @DisplayName(
        "GET /api/notifications/my/unread-count - Lấy Số lượng Thông báo Chưa đọc - Thành công")
    void getMyUnreadCount_success() throws Exception {
      when(notificationService.getMyUnreadNotificationCount(any(Authentication.class)))
          .thenReturn(5L);

      mockMvc
          .perform(get("/api/notifications/my/unread-count"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data", is(5)));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Đánh dấu Thông báo")
  class MarkNotificationTests {
    @Test
    @DisplayName("POST /api/notifications/{notificationId}/read - Đánh dấu Đã đọc - Thành công")
    void markAsRead_success() throws Exception {
      Long notificationId = 1L;
      doNothing()
          .when(notificationService)
          .markNotificationAsRead(any(Authentication.class), eq(notificationId));

      mockMvc
          .perform(post("/api/notifications/{notificationId}/read", notificationId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Notification marked as read")));
    }

    @Test
    @DisplayName("POST /api/notifications/{notificationId}/read - Thông báo không tồn tại")
    void markAsRead_notFound() throws Exception {
      Long notificationId = 99L;
      doThrow(new ResourceNotFoundException("Notification", "id", notificationId))
          .when(notificationService)
          .markNotificationAsRead(any(Authentication.class), eq(notificationId));

      mockMvc
          .perform(post("/api/notifications/{notificationId}/read", notificationId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Notification not found with id : '99'")));
    }

    @Test
    @DisplayName("POST /api/notifications/my/read-all - Đánh dấu Tất cả Đã đọc - Thành công")
    void markAllAsRead_success() throws Exception {
      doNothing().when(notificationService).markAllMyNotificationsAsRead(any(Authentication.class));

      mockMvc
          .perform(post("/api/notifications/my/read-all"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("All notifications marked as read")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xóa Thông báo")
  class DeleteNotificationTests {
    @Test
    @DisplayName("DELETE /api/notifications/{notificationId} - Xóa Thông báo - Thành công")
    void deleteNotification_success() throws Exception {
      Long notificationId = 1L;
      doNothing()
          .when(notificationService)
          .deleteNotification(any(Authentication.class), eq(notificationId));

      mockMvc
          .perform(delete("/api/notifications/{notificationId}", notificationId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Notification deleted")));
    }

    @Test
    @DisplayName("DELETE /api/notifications/{notificationId} - Thông báo không tồn tại")
    void deleteNotification_notFound() throws Exception {
      Long notificationId = 99L;
      doThrow(new ResourceNotFoundException("Notification", "id", notificationId))
          .when(notificationService)
          .deleteNotification(any(Authentication.class), eq(notificationId));

      mockMvc
          .perform(delete("/api/notifications/{notificationId}", notificationId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Notification not found with id : '99'")));
    }
  }
}
