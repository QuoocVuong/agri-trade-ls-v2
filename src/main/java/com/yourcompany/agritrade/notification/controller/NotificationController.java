package com.yourcompany.agritrade.notification.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho tất cả API notification
public class NotificationController {

  private final NotificationService notificationService;

  // Lấy danh sách thông báo của tôi (phân trang)
  @GetMapping("/my")
  public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getMyNotifications(
      Authentication authentication,
      @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<NotificationResponse> notifications =
        notificationService.getMyNotifications(authentication, pageable);
    return ResponseEntity.ok(ApiResponse.success(notifications));
  }

  // Lấy số lượng thông báo chưa đọc
  @GetMapping("/my/unread-count")
  public ResponseEntity<ApiResponse<Long>> getMyUnreadCount(Authentication authentication) {
    long count = notificationService.getMyUnreadNotificationCount(authentication);
    return ResponseEntity.ok(ApiResponse.success(count));
  }

  // Đánh dấu một thông báo là đã đọc
  @PostMapping("/{notificationId}/read")
  public ResponseEntity<ApiResponse<Void>> markAsRead(
      Authentication authentication, @PathVariable Long notificationId) {
    notificationService.markNotificationAsRead(authentication, notificationId);
    return ResponseEntity.ok(ApiResponse.success("Notification marked as read"));
  }

  // Đánh dấu tất cả thông báo là đã đọc
  @PostMapping("/my/read-all")
  public ResponseEntity<ApiResponse<Void>> markAllAsRead(Authentication authentication) {
    notificationService.markAllMyNotificationsAsRead(authentication);
    return ResponseEntity.ok(ApiResponse.success("All notifications marked as read"));
  }

  // Xóa một thông báo
  @DeleteMapping("/{notificationId}")
  public ResponseEntity<ApiResponse<Void>> deleteNotification(
      Authentication authentication, @PathVariable Long notificationId) {
    notificationService.deleteNotification(authentication, notificationId);
    return ResponseEntity.ok(ApiResponse.success("Notification deleted"));
  }
}
