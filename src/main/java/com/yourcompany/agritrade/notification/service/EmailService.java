package com.yourcompany.agritrade.notification.service;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.usermanagement.domain.User;

public interface EmailService {

  // --- User Related ---
  void sendVerificationEmail(User user, String token, String verificationUrl);

  void sendPasswordResetEmail(User user, String token, String resetUrl);

  void sendWelcomeEmail(User user); // Thêm mới

  void sendPasswordChangedEmail(User user); // Thêm mới

  // --- Order Related ---
  void sendOrderConfirmationEmailToBuyer(Order order); // Thêm mới

  void sendNewOrderNotificationToFarmer(Order order); // Thêm mới

  void sendOrderStatusUpdateEmailToBuyer(Order order, OrderStatus previousStatus); // Thêm mới

  void sendOrderCancellationEmailToBuyer(Order order); // Thêm mới

  void sendOrderCancellationNotificationToFarmer(Order order); // Thêm mới

  // --- Payment Related ---
  void sendPaymentSuccessEmailToBuyer(Order order); // Thêm mới

  void sendPaymentFailureEmailToBuyer(Order order); // Thêm mới

  // --- Product/Review/Farmer Approval Related (Thêm nếu cần) ---
  // void sendProductApprovedEmailToFarmer(Product product);
  // void sendProductRejectedEmailToFarmer(Product product, String reason);
  // void sendFarmerProfileApprovedEmail(FarmerProfile profile);
  // void sendFarmerProfileRejectedEmail(FarmerProfile profile, String reason);
  void sendProductApprovedEmailToFarmer(Product product, User farmer);

  void sendProductRejectedEmailToFarmer(Product product, String reason, User farmer);
}
