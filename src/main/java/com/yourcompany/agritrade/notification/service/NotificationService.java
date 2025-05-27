package com.yourcompany.agritrade.notification.service;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface NotificationService {

  // --- Gửi thông báo (Gọi từ các module khác) ---
  void sendOrderPlacementNotification(Order order);

  void sendOrderStatusUpdateNotification(Order order, OrderStatus previousStatus);

  void sendOrderCancellationNotification(Order order);

  void sendPaymentSuccessNotification(Order order);

  void sendPaymentFailureNotification(Order order);

  void sendNewChatMessageNotification(User recipient, User sender, Long roomId); // Ví dụ cho chat

  // ... thêm các phương thức gửi khác ...

  void sendProductApprovedNotification(Product product, User farmer);

  void sendProductRejectedNotification(Product product, String reason, User farmer);

  // --- Quản lý thông báo trong ứng dụng (Cho user hiện tại) ---
  Page<NotificationResponse> getMyNotifications(Authentication authentication, Pageable pageable);

  long getMyUnreadNotificationCount(Authentication authentication);

  void markNotificationAsRead(Authentication authentication, Long notificationId);

  void markAllMyNotificationsAsRead(Authentication authentication);

  void deleteNotification(
      Authentication authentication, Long notificationId); // Xóa mềm hoặc vật lý?

  // *** Thêm các phương thức mới cho User ***
  /** Gửi thông báo chào mừng khi user xác thực email thành công */
  void sendWelcomeNotification(User user);

  /** Gửi thông báo khi user đổi mật khẩu thành công */
  void sendPasswordChangedNotification(User user);

  /** Gửi thông báo khi trạng thái tài khoản user bị thay đổi bởi Admin */
  void sendAccountStatusUpdateNotification(User user, boolean newStatus);

  /** Gửi thông báo khi vai trò của user bị thay đổi bởi Admin */
  void sendRolesUpdateNotification(User user); // Có thể truyền thêm Set<RoleType> cũ nếu cần

  /** Gửi thông báo khi hồ sơ Farmer được duyệt */
  void sendFarmerProfileApprovedNotification(FarmerProfile profile); // *** Thêm mới ***

  /** Gửi thông báo khi hồ sơ Farmer bị từ chối */
  void sendFarmerProfileRejectedNotification(
      FarmerProfile profile, String reason); // *** Thêm mới ***

  // --- Follow Related ---
  /** Gửi thông báo cho user khi có người mới theo dõi họ */
  void sendNewFollowerNotification(User followedUser, User follower); // Thêm mới

  // --- Review Related ---
  /** Gửi thông báo cho user khi review của họ được duyệt */
  void sendReviewApprovedNotification(Review review); // Thêm mới

  /** Gửi thông báo cho user khi review của họ bị từ chối */
  void sendReviewRejectedNotification(Review review); // Thêm mới


  // --- Invoice Related ---
  void sendOverdueInvoiceReminderToBuyer(Invoice invoice); // Mới
  void sendDueSoonInvoiceReminderToBuyer(Invoice invoice);  // Mới
  void sendOverdueInvoiceNotificationToAdmin(Invoice invoice); // Mới

}
