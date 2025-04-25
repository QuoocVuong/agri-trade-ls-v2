package com.yourcompany.agritrade.notification.service.impl;

import com.yourcompany.agritrade.notification.service.EmailService;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.Payment;
import com.yourcompany.agritrade.ordering.domain.PaymentTransactionStatus;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine thymeleafTemplateEngine;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.name:AgriTradeLS}") // Thêm tên ứng dụng vào config
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
        String htmlBody = thymeleafTemplateEngine.process("mail/welcome", context); // Cần tạo template này
        sendHtmlEmail(subject, user.getEmail(), htmlBody);
    }

    @Override
    @Async("taskExecutor")
    public void sendPasswordChangedEmail(User user) {
        String subject = String.format("[%s] Mật khẩu của bạn đã được thay đổi", appName);
        Context context = new Context();
        context.setVariable("username", user.getFullName());
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/password-changed", context); // Cần tạo template này
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
        context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId()); // Link chi tiết đơn hàng
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/order-confirmation-buyer", context); // Cần tạo template này
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
        context.setVariable("orderUrl", frontendUrl + "/farmer/orders/" + order.getId()); // Link quản lý đơn hàng
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/new-order-farmer", context); // Cần tạo template này
        sendHtmlEmail(subject, order.getFarmer().getEmail(), htmlBody);
    }

    @Override
    @Async("taskExecutor")
    public void sendOrderStatusUpdateEmailToBuyer(Order order, OrderStatus previousStatus) {
        // Chỉ gửi nếu trạng thái thực sự thay đổi và là trạng thái quan trọng
        if (order.getStatus() == previousStatus) return;

        String subject = String.format("[%s] Cập nhật trạng thái đơn hàng #%s", appName, order.getOrderCode());
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("buyerName", order.getBuyer().getFullName());
        context.setVariable("newStatus", order.getStatus().name()); // Truyền tên trạng thái mới
        context.setVariable("previousStatus", previousStatus.name());
        context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/order-status-update-buyer", context); // Cần tạo template này
        sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
    }

    @Override
    @Async("taskExecutor")
    public void sendOrderCancellationEmailToBuyer(Order order) {
        String subject = String.format("[%s] Đơn hàng #%s của bạn đã bị hủy", appName, order.getOrderCode());
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("buyerName", order.getBuyer().getFullName());
        context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/order-cancellation-buyer", context); // Cần tạo template này
        sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
    }

    @Override
    @Async("taskExecutor")
    public void sendOrderCancellationNotificationToFarmer(Order order) {
        String subject = String.format("[%s] Đơn hàng #%s đã bị hủy", appName, order.getOrderCode());
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("farmerName", order.getFarmer().getFullName());
        context.setVariable("buyerName", order.getBuyer().getFullName()); // Thông báo cho farmer biết ai hủy
        context.setVariable("orderUrl", frontendUrl + "/farmer/orders/" + order.getId());
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/order-cancellation-farmer", context); // Cần tạo template này
        sendHtmlEmail(subject, order.getFarmer().getEmail(), htmlBody);
    }


    // --- Payment Related ---

    @Override
    @Async("taskExecutor")
    public void sendPaymentSuccessEmailToBuyer(Order order) {
        String subject = String.format("[%s] Thanh toán thành công cho đơn hàng #%s", appName, order.getOrderCode());
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("buyerName", order.getBuyer().getFullName());
        context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/payment-success-buyer", context); // Cần tạo template này
        sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
    }

    @Override
    @Async("taskExecutor")
    public void sendPaymentFailureEmailToBuyer(Order order) {
        String subject = String.format("[%s] Thanh toán thất bại cho đơn hàng #%s", appName, order.getOrderCode());
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("buyerName", order.getBuyer().getFullName());
        context.setVariable("orderUrl", frontendUrl + "/orders/" + order.getId());
        // Lấy thông tin lỗi thanh toán nếu có
        String paymentError = order.getPayments().stream()
                .filter(p -> p.getStatus() == PaymentTransactionStatus.FAILED)
                .map(Payment::getGatewayMessage)
                .findFirst().orElse("Không có thông tin chi tiết.");
        context.setVariable("paymentError", paymentError);
        context.setVariable("appName", appName);
        String htmlBody = thymeleafTemplateEngine.process("mail/payment-failure-buyer", context);
        sendHtmlEmail(subject, order.getBuyer().getEmail(), htmlBody);
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
            helper.setFrom(senderEmail); // Hoặc tên hiển thị: helper.setFrom(senderEmail, appName);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML content
            mailSender.send(message);
            log.info("HTML email sent successfully to {}", recipientEmail);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to {}: {}", recipientEmail, e.getMessage(), e);
            // Xem xét việc throw exception hoặc đưa vào hàng đợi retry
        } catch (Exception e) { // Bắt các lỗi khác (ví dụ: lỗi template)
            log.error("Unexpected error sending email to {}: {}", recipientEmail, e.getMessage(), e);
        }
    }
}