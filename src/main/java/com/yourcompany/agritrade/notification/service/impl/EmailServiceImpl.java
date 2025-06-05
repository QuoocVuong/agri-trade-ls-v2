package com.yourcompany.agritrade.notification.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine thymeleafTemplateEngine;

  @Value("${spring.mail.username}")
  private String senderEmail;

  @Value("${app.mail.from}")
  private String appMailFrom;

  @Value("${app.mail.sender-name}")
  private String appMailSenderName;

  @Value("${app.frontend.url:http://localhost:4200}")
  private String frontendUrl;

  @Value("${app.name:AgriTrade}")
  private String appName;

  // --- User Related ---

  @Override
  @Async("taskExecutor")
  public void sendVerificationEmail(User user, String token, String verificationUrl) {
    String subject = String.format("[%s] Xác thực tài khoản của bạn", appName);
    Context context = new Context();
    context.setVariable("username", user.getFullName());
    context.setVariable("verificationUrl", verificationUrl);
    context.setVariable("appName", appName);
    String htmlBody = thymeleafTemplateEngine.process("mail/email-verification", context);
    sendHtmlEmail(subject, user.getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendPasswordResetEmail(User user, String token, String resetUrl) {
    String subject = String.format("[%s] Yêu cầu đặt lại mật khẩu", appName);
    Context context = new Context();
    context.setVariable("username", user.getFullName());
    context.setVariable("resetUrl", resetUrl);
    context.setVariable("appName", appName);
    String htmlBody = thymeleafTemplateEngine.process("mail/password-reset", context);
    sendHtmlEmail(subject, user.getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendWelcomeEmail(User user) {
    String subject = String.format("Chào mừng bạn đến với %s!", appName);
    Context context = new Context();
    context.setVariable("username", user.getFullName());
    context.setVariable("appName", appName);
    context.setVariable("loginUrl", frontendUrl + "/auth/login"); // Ví dụ link đăng nhập
    String htmlBody =
        thymeleafTemplateEngine.process("mail/welcome", context);
    sendHtmlEmail(subject, user.getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendPasswordChangedEmail(User user) {
    String subject = String.format("[%s] Mật khẩu của bạn đã được thay đổi", appName);
    Context context = new Context();
    context.setVariable("username", user.getFullName());
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process("mail/password-changed", context);
    sendHtmlEmail(subject, user.getEmail(), htmlBody);
  }

  // --- Order Related ---

  @Override
  @Async("taskExecutor")
  public void sendOrderConfirmationEmailToBuyer(Order order) {
    String subject = String.format("[%s] Xác nhận đơn hàng #%s", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order); // Truyền cả đối tượng Order vào template
    context.setVariable("buyerName", order.getBuyer().getFullName());
    context.setVariable(
        "orderUrl", frontendUrl + "/orders/" + order.getId()); // Link chi tiết đơn hàng
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process(
            "mail/order-confirmation-buyer", context);
    sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendNewOrderNotificationToFarmer(Order order) {
    String subject = String.format("[%s] Bạn có đơn hàng mới #%s", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order);
    context.setVariable("farmerName", order.getFarmer().getFullName());
    context.setVariable("buyerName", order.getBuyer().getFullName());
    context.setVariable(
        "orderUrl", frontendUrl + "/farmer/orders/" + order.getId()); // Link quản lý đơn hàng
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process("mail/new-order-farmer", context);
    sendHtmlEmail(subject, order.getFarmer().getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendOrderStatusUpdateEmailToBuyer(Order order, OrderStatus previousStatus) {
    // Chỉ gửi nếu trạng thái thực sự thay đổi và là trạng thái quan trọng
    if (order.getStatus() == previousStatus) return;

    String subject =
        String.format("[%s] Cập nhật trạng thái đơn hàng #%s", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order);
    context.setVariable("buyerName", order.getBuyer().getFullName());
    context.setVariable("newStatus", order.getStatus().name()); // Truyền tên trạng thái mới
    context.setVariable("previousStatus", previousStatus.name());
    context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process(
            "mail/order-status-update-buyer", context);
    sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendOrderCancellationEmailToBuyer(Order order) {
    String subject =
        String.format("[%s] Đơn hàng #%s của bạn đã bị hủy", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order);
    context.setVariable("buyerName", order.getBuyer().getFullName());
    context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process(
            "mail/order-cancellation-buyer", context);
    sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendOrderCancellationNotificationToFarmer(Order order) {
    String subject = String.format("[%s] Đơn hàng #%s đã bị hủy", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order);
    context.setVariable("farmerName", order.getFarmer().getFullName());
    context.setVariable(
        "buyerName", order.getBuyer().getFullName()); // Thông báo cho farmer biết ai hủy
    context.setVariable("orderUrl", frontendUrl + "/farmer/orders/" + order.getId());
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process(
            "mail/order-cancellation-farmer", context);
    sendHtmlEmail(subject, order.getFarmer().getEmail(), htmlBody);
  }

  // --- Payment Related ---

  @Override
  @Async("taskExecutor")
  public void sendPaymentSuccessEmailToBuyer(Order order) {
    String subject =
        String.format("[%s] Thanh toán thành công cho đơn hàng #%s", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order);
    context.setVariable("buyerName", order.getBuyer().getFullName());
    context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
    context.setVariable("appName", appName);
    String htmlBody =
        thymeleafTemplateEngine.process(
            "mail/payment-success-buyer", context);
    sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendPaymentFailureEmailToBuyer(Order order) {
    String subject =
        String.format("[%s] Thanh toán thất bại cho đơn hàng #%s", appName, order.getOrderCode());
    Context context = new Context();
    context.setVariable("order", order);
    context.setVariable("buyerName", order.getBuyer().getFullName());
    context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
    // Lấy thông tin lỗi thanh toán nếu có
    String paymentError =
        order.getPayments().stream()
            .filter(p -> p.getStatus() == PaymentTransactionStatus.FAILED)
            .map(Payment::getGatewayMessage)
            .findFirst()
            .orElse("Không có thông tin chi tiết.");
    context.setVariable("paymentError", paymentError);
    context.setVariable("appName", appName);
    String htmlBody = thymeleafTemplateEngine.process("mail/payment-failure-buyer", context);
    sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
  }

  @Override
  @Async("taskExecutor")
  public void sendProductApprovedEmailToFarmer(Product product, User farmer) {
    if (farmer == null || farmer.getEmail() == null) {
      log.error(
          "Cannot send product approved email. Farmer or farmer email is null for product ID: {}",
          product.getId());
      return;
    }
    try {
      String subject = String.format("[%s] Sản phẩm của bạn đã được duyệt", appName);
      Context context = new Context();
      context.setVariable("productName", product.getName());
      context.setVariable("productUrl", frontendUrl + "/products/" + product.getSlug());
      context.setVariable("appName", appName);

      context.setVariable("farmerName", farmer.getFullName());
      String htmlBody = thymeleafTemplateEngine.process("mail/product-approved-farmer", context);

      // Sử dụng email từ đối tượng farmer đã truyền vào
      String recipientEmail = farmer.getEmail();
      log.info("Attempting to send product approved email to {}", recipientEmail);
      sendHtmlEmail(subject, recipientEmail, htmlBody);
      log.info("Successfully sent product approved email to {}", recipientEmail);
    } catch (Exception e) {
      log.error(
          "Error sending product approved email to {}: {}", farmer.getEmail(), e.getMessage(), e);
    }
  }

  @Override
  @Async("taskExecutor")
  public void sendProductRejectedEmailToFarmer(
      Product product, String reason, User farmer) {
    if (farmer == null || farmer.getEmail() == null) {
      log.error(
          "Cannot send product rejected email. Farmer or farmer email is null for product ID: {}",
          product.getId());
      return;
    }
    try {
      String subject = String.format("[%s] Sản phẩm của bạn bị từ chối", appName);
      Context context = new Context();
      context.setVariable("productName", product.getName());
      context.setVariable("reason", reason);
      context.setVariable("productUrl", frontendUrl + "/products/" + product.getSlug());
      context.setVariable("appName", appName);
      context.setVariable("farmerName", farmer.getFullName()); // Dùng farmer đã truyền vào
      String htmlBody = thymeleafTemplateEngine.process("mail/product-rejected-farmer", context);

      String recipientEmail = farmer.getEmail(); // Dùng email từ farmer đã truyền vào
      log.info("Attempting to send product rejected email to {}", recipientEmail);
      sendHtmlEmail(subject, recipientEmail, htmlBody);
      log.info("Successfully sent product rejected email to {}", recipientEmail);
    } catch (Exception e) {
      log.error(
          "Error sending product rejected email to {}: {}", farmer.getEmail(), e.getMessage(), e);
    }
  }

  // --- Invoice Related ---
  @Override
  @Async("taskExecutor")
  public void sendOverdueInvoiceReminderEmail(Invoice invoice) {
    if (invoice == null || invoice.getOrder() == null || invoice.getOrder().getBuyer() == null) {
      log.warn("Cannot send overdue invoice email: invoice, order, or buyer is null.");
      return;
    }
    User buyer = invoice.getOrder().getBuyer();
    String subject = String.format("[%s] Nhắc nhở: Hóa đơn #%s đã quá hạn thanh toán", appName, invoice.getInvoiceNumber());
    Context context = new Context();
    context.setVariable("buyerName", buyer.getFullName());
    context.setVariable("invoiceNumber", invoice.getInvoiceNumber());
    context.setVariable("orderCode", invoice.getOrder().getOrderCode());
    context.setVariable("dueDate", invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    context.setVariable("totalAmount", invoice.getTotalAmount());
    context.setVariable("orderUrl", frontendUrl + "/user/orders/" + invoice.getOrder().getId());
    context.setVariable("appName", appName);

    String htmlBody = thymeleafTemplateEngine.process("mail/invoice-overdue-reminder", context);
    sendHtmlEmail(subject, buyer.getEmail(), htmlBody);
    log.info("Sent overdue invoice reminder email for invoice {} to buyer {}", invoice.getInvoiceNumber(), buyer.getEmail());
  }

  @Override
  @Async("taskExecutor")
  public void sendDueSoonInvoiceReminderEmail(Invoice invoice) {
    if (invoice == null || invoice.getOrder() == null || invoice.getOrder().getBuyer() == null) {
      log.warn("Cannot send due soon invoice email: invoice, order, or buyer is null.");
      return;
    }
    User buyer = invoice.getOrder().getBuyer();
    String subject = String.format("[%s] Nhắc nhở: Hóa đơn #%s sắp đến hạn thanh toán", appName, invoice.getInvoiceNumber());
    Context context = new Context();
    context.setVariable("buyerName", buyer.getFullName());
    context.setVariable("invoiceNumber", invoice.getInvoiceNumber());
    context.setVariable("orderCode", invoice.getOrder().getOrderCode());
    context.setVariable("dueDate", invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    context.setVariable("totalAmount", invoice.getTotalAmount());
    context.setVariable("orderUrl", frontendUrl + "/user/orders/" + invoice.getOrder().getId());
    context.setVariable("appName", appName);

    String htmlBody = thymeleafTemplateEngine.process("mail/invoice-due-soon-reminder", context);
    sendHtmlEmail(subject, buyer.getEmail(), htmlBody);
    log.info("Sent due soon invoice reminder email for invoice {} to buyer {}", invoice.getInvoiceNumber(), buyer.getEmail());
  }

  @Override
  @Async("taskExecutor")
  public void sendOverdueInvoiceAdminEmail(Invoice invoice, List<User> adminUsers) {
    if (invoice == null || adminUsers == null || adminUsers.isEmpty()) {
      log.warn("Cannot send overdue invoice admin email: invoice or adminUsers list is null/empty.");
      return;
    }
    String subject = String.format("[%s] Cảnh báo: Hóa đơn #%s đã quá hạn", appName, invoice.getInvoiceNumber());
    Context context = new Context();
    context.setVariable("invoiceNumber", invoice.getInvoiceNumber());
    context.setVariable("orderCode", invoice.getOrder() != null ? invoice.getOrder().getOrderCode() : "N/A");
    context.setVariable("buyerName", invoice.getOrder() != null && invoice.getOrder().getBuyer() != null ? invoice.getOrder().getBuyer().getFullName() : "N/A");
    context.setVariable("dueDate", invoice.getDueDate() != null ? invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A");
    context.setVariable("totalAmount", invoice.getTotalAmount());
    context.setVariable("adminOrderUrl", frontendUrl + "/admin/orders/" + (invoice.getOrder() != null ? invoice.getOrder().getId() : ""));
    context.setVariable("appName", appName);

    String htmlBody = thymeleafTemplateEngine.process("mail/invoice-overdue-admin-alert", context);

    for (User admin : adminUsers) {
      if (admin != null && StringUtils.hasText(admin.getEmail())) {
        sendHtmlEmail(subject, admin.getEmail(), htmlBody);
        log.info("Sent overdue invoice admin alert for invoice {} to admin {}", invoice.getInvoiceNumber(), admin.getEmail());
      }
    }
  }



  // --- Private Helper Method ---
  private void sendHtmlEmail(String subject, String recipientEmail, String htmlContent) {
    if (!StringUtils.hasText(recipientEmail)) {
      log.warn("Skipping email send: Recipient email is empty. Subject: {}", subject);
      return;
    }
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(
          appMailFrom,
          appMailSenderName);
      helper.setTo(recipientEmail);
      helper.setSubject(subject);
      helper.setText(htmlContent, true); // true = HTML content
      mailSender.send(message);
      log.info("HTML email sent successfully to {}", recipientEmail);
    } catch (MessagingException e) {
      log.error("Failed to send HTML email to {}: {}", recipientEmail, e.getMessage(), e);

    } catch (Exception e) {
      log.error("Unexpected error sending email to {}: {}", recipientEmail, e.getMessage(), e);
    }
  }
}
