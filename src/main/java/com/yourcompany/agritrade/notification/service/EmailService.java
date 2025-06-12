package com.yourcompany.agritrade.notification.service;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.usermanagement.domain.User;
import java.util.List;

public interface EmailService {

  // --- User Related ---
  void sendVerificationEmail(User user, String token, String verificationUrl);

  void sendPasswordResetEmail(User user, String token, String resetUrl);

  void sendWelcomeEmail(User user);

  void sendPasswordChangedEmail(User user);

  // --- Order Related ---
  void sendOrderConfirmationEmailToBuyer(Order order);

  void sendNewOrderNotificationToFarmer(Order order);

  void sendOrderStatusUpdateEmailToBuyer(Order order, OrderStatus previousStatus);

  void sendOrderCancellationEmailToBuyer(Order order);

  void sendOrderCancellationNotificationToFarmer(Order order);

  // --- Payment Related ---
  void sendPaymentSuccessEmailToBuyer(Order order);

  void sendPaymentFailureEmailToBuyer(Order order);

  void sendProductApprovedEmailToFarmer(Product product, User farmer);

  void sendProductRejectedEmailToFarmer(Product product, String reason, User farmer);

  // --- Invoice Related ---
  void sendOverdueInvoiceReminderEmail(Invoice invoice);

  void sendDueSoonInvoiceReminderEmail(Invoice invoice);

  void sendOverdueInvoiceAdminEmail(Invoice invoice, List<User> adminUsers);
}
