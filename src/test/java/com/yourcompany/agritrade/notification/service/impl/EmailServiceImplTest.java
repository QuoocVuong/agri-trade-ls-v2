package com.yourcompany.agritrade.notification.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

  @Mock private JavaMailSender mailSender;
  @Mock private SpringTemplateEngine thymeleafTemplateEngine;

  @InjectMocks private EmailServiceImpl emailService;

  private User testUser, testBuyer, testFarmer, testAdmin;
  private Order testOrder;
  private Product testProduct;
  private Invoice testInvoice;
  private MimeMessage mimeMessage;

  private final String FRONTEND_URL = "http://test-frontend.com";
  private final String APP_NAME = "TestAgriTrade";
  private final String APP_MAIL_FROM = "noreply@testagritrade.com";
  private final String APP_SENDER_NAME = "Test AgriTrade Platform";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(emailService, "frontendUrl", FRONTEND_URL);
    ReflectionTestUtils.setField(emailService, "appName", APP_NAME);
    ReflectionTestUtils.setField(emailService, "appMailFrom", APP_MAIL_FROM);
    ReflectionTestUtils.setField(emailService, "appMailSenderName", APP_SENDER_NAME);

    testUser = new User();
    testUser.setId(1L);
    testUser.setEmail("user@example.com");
    testUser.setFullName("Test User FullName");

    testBuyer = new User();
    testBuyer.setId(2L); // ID khác với testUser
    testBuyer.setEmail("buyer@example.com");
    testBuyer.setFullName("Test Buyer FullName");

    testFarmer = new User();
    testFarmer.setId(3L); // ID khác
    testFarmer.setEmail("farmer@example.com");
    testFarmer.setFullName("Test Farmer FullName");

    testAdmin = new User();
    testAdmin.setId(4L); // ID khác
    testAdmin.setEmail("admin@example.com");
    testAdmin.setFullName("Test Admin FullName");

    testOrder = new Order();
    testOrder.setId(10L);
    testOrder.setOrderCode("ORD123");
    testOrder.setBuyer(testBuyer); // SỬA: Gán testBuyer làm người mua của đơn hàng
    testOrder.setFarmer(testFarmer);
    testOrder.setStatus(OrderStatus.PROCESSING);
    testOrder.setTotalAmount(new BigDecimal("100.00"));
    testOrder.setPayments(Collections.emptySet());

    testProduct = new Product();
    testProduct.setId(20L);
    testProduct.setName("Test Product");
    testProduct.setSlug("test-product");

    testInvoice = new Invoice();
    testInvoice.setId(30L);
    testInvoice.setOrder(testOrder); // testOrder bây giờ có buyer là testBuyer
    testInvoice.setInvoiceNumber("INV-ORD123");
    testInvoice.setDueDate(LocalDate.now().plusDays(5));
    testInvoice.setTotalAmount(testOrder.getTotalAmount());

    mimeMessage = new MimeMessage((Session) null);
    lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage); // THÊM lenient()
  }

  private void verifyEmailSent(
      String expectedTemplateName, String expectedRecipient, String expectedSubjectContains)
      throws MessagingException {
    ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    // Sử dụng lenient() cho verify nếu template engine có thể không được gọi trong một số luồng (ví
    // dụ: recipient rỗng)
    // Tuy nhiên, trong các test case thành công, nó phải được gọi.
    verify(thymeleafTemplateEngine).process(eq(expectedTemplateName), contextCaptor.capture());

    ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender).send(messageCaptor.capture());

    MimeMessage sentMessage = messageCaptor.getValue();
    assertTrue(
        Arrays.stream(sentMessage.getRecipients(MimeMessage.RecipientType.TO))
            .anyMatch(addr -> addr.toString().equals(expectedRecipient)),
        "Expected recipient "
            + expectedRecipient
            + " not found in "
            + Arrays.toString(sentMessage.getRecipients(MimeMessage.RecipientType.TO)));
    assertTrue(
        sentMessage.getSubject().contains(expectedSubjectContains),
        "Expected subject to contain '"
            + expectedSubjectContains
            + "', but was '"
            + sentMessage.getSubject()
            + "'");
  }

  @Nested
  @DisplayName("User Related Email Tests")
  class UserRelatedEmailTests {
    @Test
    @DisplayName("Send Verification Email")
    void sendVerificationEmail_shouldProcessTemplateAndSend() throws MessagingException {
      String token = "test-token";
      String verificationUrl = FRONTEND_URL + "/verify?token=" + token;
      when(thymeleafTemplateEngine.process(eq("mail/email-verification"), any(Context.class)))
          .thenReturn("<html>Verification HTML</html>");

      emailService.sendVerificationEmail(testUser, token, verificationUrl);

      verifyEmailSent("mail/email-verification", testUser.getEmail(), "Xác thực tài khoản");
      ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
      // verify(thymeleafTemplateEngine).process(anyString(), contextCaptor.capture()); // Dòng này
      // thừa nếu đã verify ở trên
      verify(thymeleafTemplateEngine)
          .process(eq("mail/email-verification"), contextCaptor.capture());
      assertEquals(testUser.getFullName(), contextCaptor.getValue().getVariable("username"));
      assertEquals(verificationUrl, contextCaptor.getValue().getVariable("verificationUrl"));
    }

    @Test
    @DisplayName("Send Welcome Email")
    void sendWelcomeEmail_shouldProcessTemplateAndSend() throws MessagingException {
      when(thymeleafTemplateEngine.process(eq("mail/welcome"), any(Context.class)))
          .thenReturn("<html>Welcome HTML</html>");
      emailService.sendWelcomeEmail(testUser);
      verifyEmailSent("mail/welcome", testUser.getEmail(), "Chào mừng bạn đến với");
    }
  }

  @Nested
  @DisplayName("Order Related Email Tests")
  class OrderRelatedEmailTests {
    @Test
    @DisplayName("Send Order Confirmation Email To Buyer")
    void sendOrderConfirmationEmailToBuyer_shouldProcessAndSend() throws MessagingException {
      when(thymeleafTemplateEngine.process(eq("mail/order-confirmation-buyer"), any(Context.class)))
          .thenReturn("<html>Order Confirmation</html>");
      emailService.sendOrderConfirmationEmailToBuyer(
          testOrder); // testOrder.getBuyer() là testBuyer
      verifyEmailSent(
          "mail/order-confirmation-buyer",
          testBuyer.getEmail(),
          "Xác nhận đơn hàng #" + testOrder.getOrderCode());
    }

    @Test
    @DisplayName("Send Order Status Update Email To Buyer - Status Changed")
    void sendOrderStatusUpdateEmailToBuyer_whenStatusChanged_shouldSendEmail()
        throws MessagingException {
      testOrder.setStatus(OrderStatus.SHIPPING);
      OrderStatus previousStatus = OrderStatus.PROCESSING;
      when(thymeleafTemplateEngine.process(
              eq("mail/order-status-update-buyer"), any(Context.class)))
          .thenReturn("<html>Status Update</html>");

      emailService.sendOrderStatusUpdateEmailToBuyer(testOrder, previousStatus);

      verifyEmailSent(
          "mail/order-status-update-buyer",
          testBuyer.getEmail(),
          "Cập nhật trạng thái đơn hàng #" + testOrder.getOrderCode());
    }

    @Test
    @DisplayName("Send Order Status Update Email To Buyer - Status Not Changed - Should Not Send")
    void sendOrderStatusUpdateEmailToBuyer_whenStatusNotChanged_shouldNotSendEmail() {
      testOrder.setStatus(OrderStatus.PROCESSING);
      OrderStatus previousStatus = OrderStatus.PROCESSING;

      emailService.sendOrderStatusUpdateEmailToBuyer(testOrder, previousStatus);

      verify(thymeleafTemplateEngine, never()).process(anyString(), any(Context.class));
      verify(mailSender, never()).send(any(MimeMessage.class));
    }
  }

  @Nested
  @DisplayName("Product Approval Email Tests")
  class ProductApprovalEmailTests {
    @Test
    @DisplayName("Send Product Approved Email To Farmer - Success")
    void sendProductApprovedEmailToFarmer_success() throws MessagingException {
      when(thymeleafTemplateEngine.process(eq("mail/product-approved-farmer"), any(Context.class)))
          .thenReturn("<html>Approved</html>");
      emailService.sendProductApprovedEmailToFarmer(testProduct, testFarmer);
      verifyEmailSent(
          "mail/product-approved-farmer", testFarmer.getEmail(), "Sản phẩm của bạn đã được duyệt");
    }

    @Test
    @DisplayName("Send Product Approved Email To Farmer - Farmer Null - Should Not Send")
    void sendProductApprovedEmailToFarmer_whenFarmerNull_shouldNotSend() {
      emailService.sendProductApprovedEmailToFarmer(testProduct, null);
      verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Send Product Approved Email To Farmer - Farmer Email Null - Should Not Send")
    void sendProductApprovedEmailToFarmer_whenFarmerEmailNull_shouldNotSend() {
      testFarmer.setEmail(null);
      emailService.sendProductApprovedEmailToFarmer(testProduct, testFarmer);
      verify(mailSender, never()).send(any(MimeMessage.class));
    }
  }

  @Nested
  @DisplayName("Invoice Email Tests")
  class InvoiceEmailTests {
    @Test
    @DisplayName("Send Overdue Invoice Reminder Email - Success")
    void sendOverdueInvoiceReminderEmail_success() throws MessagingException {
      when(thymeleafTemplateEngine.process(eq("mail/invoice-overdue-reminder"), any(Context.class)))
          .thenReturn("<html>Overdue</html>");
      emailService.sendOverdueInvoiceReminderEmail(
          testInvoice); // testInvoice.getOrder().getBuyer() là testBuyer
      verifyEmailSent(
          "mail/invoice-overdue-reminder",
          testBuyer.getEmail(),
          "Hóa đơn #" + testInvoice.getInvoiceNumber() + " đã quá hạn");
    }

    @Test
    @DisplayName("Send Overdue Invoice Admin Email - Success")
    void sendOverdueInvoiceAdminEmail_success() throws MessagingException {
      when(thymeleafTemplateEngine.process(
              eq("mail/invoice-overdue-admin-alert"), any(Context.class)))
          .thenReturn("<html>Admin Alert</html>");
      emailService.sendOverdueInvoiceAdminEmail(testInvoice, List.of(testAdmin));
      verifyEmailSent(
          "mail/invoice-overdue-admin-alert",
          testAdmin.getEmail(),
          "Cảnh báo: Hóa đơn #" + testInvoice.getInvoiceNumber() + " đã quá hạn");
    }

    @Test
    @DisplayName("Send Overdue Invoice Admin Email - No Admins - Should Not Send")
    void sendOverdueInvoiceAdminEmail_whenNoAdmins_shouldNotSend() {
      emailService.sendOverdueInvoiceAdminEmail(testInvoice, Collections.emptyList());
      verify(mailSender, never()).send(any(MimeMessage.class));
    }
  }

  @Nested
  @DisplayName("Helper Method sendHtmlEmail Tests")
  class SendHtmlEmailHelperTests {
    @Test
    @DisplayName("sendHtmlEmail - Recipient Email Empty - Should Not Send")
    void sendHtmlEmail_whenRecipientEmailEmpty_shouldNotSend() {
      testUser.setEmail("");
      emailService.sendWelcomeEmail(testUser);
      verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendHtmlEmail - MailSendException Occurs - Should Log Error")
    void sendHtmlEmail_whenMailSendException_shouldLogError() { // Bỏ throws MessagingException
      when(thymeleafTemplateEngine.process(anyString(), any(Context.class)))
          .thenReturn("<html></html>");
      doThrow(new MailSendException("Test send failed")) // SỬA: Dùng MailSendException
          .when(mailSender)
          .send(any(MimeMessage.class));

      testUser.setEmail("user@example.com"); // Đảm bảo email hợp lệ để không bị return sớm
      emailService.sendWelcomeEmail(testUser);

      verify(mailSender).send(any(MimeMessage.class));
    }
  }
}
