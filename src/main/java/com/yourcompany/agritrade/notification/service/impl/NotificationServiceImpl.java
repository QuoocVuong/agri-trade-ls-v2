package com.yourcompany.agritrade.notification.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.notification.domain.Notification;
import com.yourcompany.agritrade.notification.dto.response.NotificationResponse;
import com.yourcompany.agritrade.notification.mapper.NotificationMapper;
import com.yourcompany.agritrade.notification.repository.NotificationRepository;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.notification.service.InAppNotificationService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationMapper notificationMapper;
  private final EmailService emailService; // Inject EmailService
  private final InAppNotificationService
      inAppNotificationService; // Inject InAppNotificationService
  private final UserRepository userRepository; // Inject UserRepository

  @Value("${app.frontend.url:http://localhost:4200}")
  private String frontendUrl;

  // --- Gửi thông báo ---

  @Override
  public void sendOrderPlacementNotification(Order order) {
    // Gửi cho người mua
    String buyerMsg =
        String.format("Đơn hàng #%s của bạn đã được đặt thành công.", order.getOrderCode());
    String buyerLink = frontendUrl + "/user/orders/" + order.getId(); // Link đến chi tiết đơn hàng
    inAppNotificationService.createAndSendInAppNotification(
        order.getBuyer(), buyerMsg, NotificationType.ORDER_PLACED, buyerLink);
     //emailService.sendOrderConfirmationEmailToBuyer(order); // Gọi gửi mail

    // Gửi cho người bán
    String farmerMsg =
        String.format(
            "Bạn có đơn hàng mới #%s từ %s.", order.getOrderCode(), order.getBuyer().getFullName());
    String farmerLink = frontendUrl + "/farmer/orders/" + order.getId();
    inAppNotificationService.createAndSendInAppNotification(
        order.getFarmer(), farmerMsg, NotificationType.ORDER_PLACED, farmerLink);
     //emailService.sendNewOrderNotificationToFarmer(order); // Gọi gửi mail
  }

  @Override
  public void sendOrderStatusUpdateNotification(Order order, OrderStatus previousStatus) {
    // Gửi cho người mua
    String buyerMsg =
        String.format(
            "Đơn hàng #%s của bạn đã được cập nhật trạng thái thành: %s.",
            order.getOrderCode(), order.getStatus().name());
    String buyerLink = frontendUrl + "/user/orders/" + order.getId();
    inAppNotificationService.createAndSendInAppNotification(
        order.getBuyer(), buyerMsg, NotificationType.ORDER_STATUS_UPDATE, buyerLink);
     //emailService.sendOrderStatusUpdateEmailToBuyer(order, previousStatus);

    // Gửi cho người bán (nếu trạng thái thay đổi không phải do chính họ) - cần thêm logic kiểm tra
    // người thực hiện
    if (order.getStatus() == OrderStatus.DELIVERED
        || order.getStatus()
            == OrderStatus.CANCELLED) { // Ví dụ chỉ báo cho farmer khi hoàn thành/hủy
      String farmerMsg =
          String.format(
              "Đơn hàng #%s đã cập nhật trạng thái thành: %s.",
              order.getOrderCode(), order.getStatus().name());
      String farmerLink = frontendUrl + "/farmer/orders/" + order.getId();
      inAppNotificationService.createAndSendInAppNotification(
          order.getFarmer(), farmerMsg, NotificationType.ORDER_STATUS_UPDATE, farmerLink);
    }
  }

  @Override
  public void sendOrderCancellationNotification(Order order) {
    // Gửi cho người mua
    String buyerMsg = String.format("Đơn hàng #%s của bạn đã bị hủy.", order.getOrderCode());
    String buyerLink = frontendUrl + "/user/orders/" + order.getId();
    inAppNotificationService.createAndSendInAppNotification(
        order.getBuyer(), buyerMsg, NotificationType.ORDER_CANCELLED, buyerLink);
     //emailService.sendOrderCancellationEmailToBuyer(order);

    // Gửi cho người bán
    String farmerMsg = String.format("Đơn hàng #%s đã bị hủy.", order.getOrderCode());
    String farmerLink = frontendUrl + "/farmer/orders/" + order.getId();
    inAppNotificationService.createAndSendInAppNotification(
        order.getFarmer(), farmerMsg, NotificationType.ORDER_CANCELLED, farmerLink);
     //emailService.sendOrderCancellationNotificationToFarmer(order);
  }

  @Override
  public void sendPaymentSuccessNotification(Order order) {
    String buyerMsg =
        String.format("Thanh toán cho đơn hàng #%s đã thành công.", order.getOrderCode());
    String buyerLink = frontendUrl + "/user/orders/" + order.getId();
    inAppNotificationService.createAndSendInAppNotification(
        order.getBuyer(), buyerMsg, NotificationType.PAYMENT_SUCCESS, buyerLink);
     //emailService.sendPaymentSuccessEmailToBuyer(order);
    // Thông báo cho farmer nếu cần
  }

  @Override
  public void sendPaymentFailureNotification(Order order) {
    String buyerMsg =
        String.format("Thanh toán cho đơn hàng #%s đã thất bại.", order.getOrderCode());
    String buyerLink = frontendUrl + "/user/orders/" + order.getId();
    inAppNotificationService.createAndSendInAppNotification(
        order.getBuyer(), buyerMsg, NotificationType.PAYMENT_FAILURE, buyerLink);
     //emailService.sendPaymentFailureEmailToBuyer(order);
  }

  @Override
  public void sendProductApprovedNotification(Product product, User farmer) { // Thêm User farmer
    if (farmer == null) {
      log.error(
          "Cannot send product approved notification: Farmer is null for product ID: {}",
          product.getId());
      return;
    }
    log.info(
        "Sending product approved notification for product {} to farmer {}",
        product.getId(),
        farmer.getId());
    String message = String.format("Sản phẩm '%s' của bạn đã được duyệt.", product.getName());
    String link = frontendUrl + "/products/" + product.getSlug();
    // Sử dụng đối tượng farmer đã được truyền vào
    inAppNotificationService.createAndSendInAppNotification(
        farmer, message, NotificationType.PRODUCT_APPROVED, link);
  }

  @Override
  public void sendProductRejectedNotification(Product product, String reason, User farmer) {
    if (farmer == null) {
      log.error(
          "Cannot send product rejected notification: Farmer is null for product ID: {}",
          product.getId());
      return;
    }
    log.info(
        "Sending product rejected notification for product {} to farmer {}",
        product.getId(),
        farmer.getId());
    String message =
        String.format("Sản phẩm '%s' của bạn đã bị từ chối. Lý do: %s", product.getName(), reason);
    String link = frontendUrl + "/farmer/products/";
    inAppNotificationService.createAndSendInAppNotification(
        farmer, message, NotificationType.PRODUCT_REJECTED, link);
  }

  @Override
  public void sendNewChatMessageNotification(User recipient, User sender, Long roomId) {
    String message = String.format("Bạn có tin nhắn mới từ %s.", sender.getFullName());
    String link = frontendUrl + "/chat?roomId=" + roomId; // Link đến phòng chat
    inAppNotificationService.createAndSendInAppNotification(
        recipient, message, NotificationType.NEW_MESSAGE, link);
    // Có thể không cần gửi email cho mỗi tin nhắn mới, trừ khi user offline lâu
  }

  // --- Quản lý thông báo trong ứng dụng ---

  @Override
  @Transactional(readOnly = true)
  public Page<NotificationResponse> getMyNotifications(
      Authentication authentication, Pageable pageable) {
    User recipient = getUserFromAuthentication(authentication);
    Page<Notification> notificationPage =
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipient.getId(), pageable);
    return notificationMapper.toNotificationResponsePage(notificationPage);
  }

  @Override
  @Transactional(readOnly = true)
  public long getMyUnreadNotificationCount(Authentication authentication) {
    User recipient = getUserFromAuthentication(authentication);
    return notificationRepository.countByRecipientIdAndIsReadFalse(recipient.getId());
  }

  @Override
  @Transactional
  public void markNotificationAsRead(Authentication authentication, Long notificationId) {
    User recipient = getUserFromAuthentication(authentication);
    int updated =
        notificationRepository.markAsRead(notificationId, recipient.getId(), LocalDateTime.now());
    if (updated == 0) {
      // Có thể do notification không tồn tại hoặc không thuộc user
      log.warn(
          "Failed to mark notification {} as read for user {}", notificationId, recipient.getId());
      // Không cần throw lỗi nghiêm trọng, chỉ cần không cập nhật
    } else {
      log.info("Marked notification {} as read for user {}", notificationId, recipient.getId());
    }
  }

  @Override
  @Transactional
  public void markAllMyNotificationsAsRead(Authentication authentication) {
    User recipient = getUserFromAuthentication(authentication);
    int updatedCount =
        notificationRepository.markAllAsReadForRecipient(recipient.getId(), LocalDateTime.now());
    log.info("Marked {} notifications as read for user {}", updatedCount, recipient.getId());
  }

  @Override
  @Transactional
  public void deleteNotification(Authentication authentication, Long notificationId) {
    User recipient = getUserFromAuthentication(authentication);
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
    // Kiểm tra ownership
    if (!notification.getRecipient().getId().equals(recipient.getId())) {
      throw new AccessDeniedException("User does not own this notification");
    }
    notificationRepository.delete(notification); // Xóa vật lý
    log.info("Deleted notification {} for user {}", notificationId, recipient.getId());
  }

  // Helper method
  private User getUserFromAuthentication(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new AccessDeniedException("User is not authenticated");
    }
    String email = authentication.getName();
    return userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
  }

  @Override
  public void sendWelcomeNotification(User user) {
    log.info("Sending welcome notification to user {}", user.getId());
    // Gửi Email chào mừng
    emailService.sendWelcomeEmail(user);

    // Gửi In-App notification chào mừng
    String message = "Chào mừng bạn đến với AgriTradeLS! Hãy bắt đầu khám phá nông sản Lạng Sơn.";
    String link = frontendUrl; // Link trang chủ
    inAppNotificationService.createAndSendInAppNotification(
        user, message, NotificationType.WELCOME, link);
  }

  @Override
  public void sendPasswordChangedNotification(User user) {
    log.info("Sending password changed notification to user {}", user.getId());
    // Gửi Email thông báo đổi mật khẩu
    emailService.sendPasswordChangedEmail(user);

    // Gửi In-App notification (tùy chọn, có thể không cần thiết)
    // String message = "Mật khẩu của bạn đã được thay đổi thành công.";
    // inAppNotificationService.createAndSendInAppNotification(user, message,
    // NotificationType.PASSWORD_RESET, null);
  }

  @Override
  public void sendAccountStatusUpdateNotification(User user, boolean newStatus) {
    log.info(
        "Sending account status update notification to user {}. New status: {}",
        user.getId(),
        newStatus ? "ACTIVE" : "INACTIVE");
    String statusText = newStatus ? "kích hoạt" : "vô hiệu hóa";
    String message = String.format("Tài khoản của bạn đã được %s bởi quản trị viên.", statusText);
    String link = frontendUrl + "/profile"; // Link đến trang profile

    // Gửi In-App notification
    inAppNotificationService.createAndSendInAppNotification(
        user, message, NotificationType.OTHER, link); // Có thể tạo Type riêng

    // Gửi Email thông báo (tùy chọn)
    // emailService.sendAccountStatusUpdateEmail(user, newStatus); // Cần tạo hàm này và template
  }

  @Override
  public void sendRolesUpdateNotification(User user) {
    log.info("Sending roles update notification to user {}", user.getId());
    String rolesString =
        user.getRoles().stream()
            .map(role -> role.getName().toString().replace("ROLE_", "")) // Bỏ tiền tố ROLE_
            .collect(Collectors.joining(", "));
    String message =
        String.format("Vai trò của bạn trên hệ thống đã được cập nhật thành: %s.", rolesString);
    String link = frontendUrl + "/profile";

    // Gửi In-App notification
    inAppNotificationService.createAndSendInAppNotification(
        user, message, NotificationType.OTHER, link); // Có thể tạo Type riêng

    // Gửi Email thông báo (tùy chọn)
    // emailService.sendRolesUpdateEmail(user); // Cần tạo hàm này và template
  }

  @Override
  public void sendNewFollowerNotification(User followedUser, User follower) {
    log.info("Sending new follower notification to user {}", followedUser.getId());
    String message =
        String.format("<strong>%s</strong> đã bắt đầu theo dõi bạn.", follower.getFullName());
    // Link đến trang profile của người follower
    String link = frontendUrl + "/profile/" + follower.getId(); // Giả sử có route này
    inAppNotificationService.createAndSendInAppNotification(
        followedUser, message, NotificationType.NEW_FOLLOWER, link);
    // Có thể gửi cả email nếu muốn
    // emailService.sendNewFollowerEmail(followedUser, follower);
  }

  @Override
  public void sendReviewApprovedNotification(Review review) {
    log.info("Sending review approved notification for review {}", review.getId());
    String productName = review.getProduct() != null ? review.getProduct().getName() : "sản phẩm";
    String message =
        String.format("Đánh giá của bạn cho sản phẩm '%s' đã được duyệt.", productName);
    // Link đến sản phẩm đã review
    String link =
        (review.getProduct() != null)
            ? frontendUrl + "/products/" + review.getProduct().getSlug()
            : null;
    inAppNotificationService.createAndSendInAppNotification(
        review.getConsumer(), message, NotificationType.REVIEW_APPROVED, link);
    // Có thể gửi cả email
    // emailService.sendReviewApprovedEmail(review);
  }

  @Override
  public void sendReviewRejectedNotification(Review review) {
    log.info("Sending review rejected notification for review {}", review.getId());
    String productName = review.getProduct() != null ? review.getProduct().getName() : "sản phẩm";
    String message =
        String.format(
            "Rất tiếc, đánh giá của bạn cho sản phẩm '%s' đã không được duyệt.", productName);
    // Có thể không cần link hoặc link đến trang quản lý review của user
    String link = frontendUrl + "/my-reviews"; // Ví dụ
    inAppNotificationService.createAndSendInAppNotification(
        review.getConsumer(), message, NotificationType.REVIEW_REJECTED, link);
    // Có thể gửi cả email
    // emailService.sendReviewRejectedEmail(review);
  }

  // *** Implement các phương thức mới cho duyệt Farmer Profile ***

  @Override
  public void sendFarmerProfileApprovedNotification(FarmerProfile profile) {
    if (profile == null || profile.getUser() == null) {
      log.warn("Cannot send farmer approval notification: profile or user is null.");
      return;
    }
    User farmer = profile.getUser();
    log.info("Sending farmer profile approved notification to user {}", farmer.getId());

    // Gửi In-App notification
    String message =
        String.format(
            "Chúc mừng! Hồ sơ nông dân '%s' của bạn đã được duyệt.", profile.getFarmName());
    String link = frontendUrl + "/farmer/dashboard"; // Link đến dashboard farmer
    inAppNotificationService.createAndSendInAppNotification(
        farmer, message, NotificationType.FARMER_PROFILE_APPROVED, link);

    // Gửi Email thông báo (tùy chọn)
    // emailService.sendFarmerProfileApprovedEmail(profile); // Cần tạo hàm này và template trong
    // EmailService
  }

  @Override
  public void sendFarmerProfileRejectedNotification(FarmerProfile profile, String reason) {
    if (profile == null || profile.getUser() == null) {
      log.warn("Cannot send farmer rejection notification: profile or user is null.");
      return;
    }
    User farmer = profile.getUser();
    log.info(
        "Sending farmer profile rejected notification to user {}. Reason: {}",
        farmer.getId(),
        reason);

    // Gửi In-App notification
    String reasonText = StringUtils.hasText(reason) ? " Lý do: " + reason : "";
    String message =
        String.format(
            "Rất tiếc, hồ sơ nông dân '%s' của bạn đã bị từ chối.%s",
            profile.getFarmName(), reasonText);
    String link = frontendUrl + "/user/profile/farmer-profile"; // Link đến trang sửa profile farmer
    inAppNotificationService.createAndSendInAppNotification(
        farmer, message, NotificationType.FARMER_PROFILE_REJECTED, link);

    // Gửi Email thông báo (tùy chọn)
    // emailService.sendFarmerProfileRejectedEmail(profile, reason); // Cần tạo hàm này và template
    // trong EmailService
  }


  // --- Invoice Related ---
  @Override
  public void sendOverdueInvoiceReminderToBuyer(Invoice invoice) {
    if (invoice == null || invoice.getOrder() == null || invoice.getOrder().getBuyer() == null) {
      log.warn("Cannot send overdue invoice reminder: invoice, order, or buyer is null.");
      return;
    }
    User buyer = invoice.getOrder().getBuyer();
    String message = String.format("Hóa đơn #%s (Đơn hàng #%s) của bạn đã quá hạn thanh toán (Ngày đáo hạn: %s). Vui lòng thanh toán sớm.",
            invoice.getInvoiceNumber(),
            invoice.getOrder().getOrderCode(),
            invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    String link = frontendUrl + "/user/orders/" + invoice.getOrder().getId();
    inAppNotificationService.createAndSendInAppNotification(buyer, message, NotificationType.INVOICE_OVERDUE, link);
    // emailService.sendOverdueInvoiceReminderEmail(invoice); // Đã gọi từ Scheduler
  }

  @Override
  public void sendDueSoonInvoiceReminderToBuyer(Invoice invoice) {
    if (invoice == null || invoice.getOrder() == null || invoice.getOrder().getBuyer() == null) {
      log.warn("Cannot send due soon invoice reminder: invoice, order, or buyer is null.");
      return;
    }
    User buyer = invoice.getOrder().getBuyer();
    String message = String.format("Hóa đơn #%s (Đơn hàng #%s) của bạn sắp đến hạn thanh toán vào ngày %s.",
            invoice.getInvoiceNumber(),
            invoice.getOrder().getOrderCode(),
            invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    String link = frontendUrl + "/user/orders/" + invoice.getOrder().getId();
    inAppNotificationService.createAndSendInAppNotification(buyer, message, NotificationType.INVOICE_DUE_SOON, link);
    // emailService.sendDueSoonInvoiceReminderEmail(invoice); // Đã gọi từ Scheduler
  }

  @Override
  public void sendOverdueInvoiceNotificationToAdmin(Invoice invoice) {
    if (invoice == null) {
      log.warn("Cannot send overdue invoice admin notification: invoice is null.");
      return;
    }
    List<User> admins = userRepository.findByRoles_Name(RoleType.ROLE_ADMIN);
    if (admins.isEmpty()) {
      log.warn("No admin users found to send overdue invoice notification for invoice {}", invoice.getInvoiceNumber());
      return;
    }
    String buyerInfo = (invoice.getOrder() != null && invoice.getOrder().getBuyer() != null)
            ? invoice.getOrder().getBuyer().getFullName() + " (ID: " + invoice.getOrder().getBuyer().getId() + ")"
            : "Không rõ";
    String message = String.format("CẢNH BÁO: Hóa đơn #%s (Đơn hàng #%s, Khách hàng: %s) đã QUÁ HẠN thanh toán.",
            invoice.getInvoiceNumber(),
            invoice.getOrder() != null ? invoice.getOrder().getOrderCode() : "N/A",
            buyerInfo);
    String link = frontendUrl + "/admin/invoices?invoiceNumber=" + invoice.getInvoiceNumber(); // Link tới trang quản lý hóa đơn của admin

    for (User admin : admins) {
      inAppNotificationService.createAndSendInAppNotification(admin, message, NotificationType.ADMIN_ALERT, link);
    }
    // emailService.sendOverdueInvoiceAdminEmail(invoice, admins); // Đã gọi từ Scheduler
  }



}
