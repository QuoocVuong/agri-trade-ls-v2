package com.yourcompany.agritradels.notification.service;

import com.yourcompany.agritradels.ordering.domain.Order; // Import Order
import com.yourcompany.agritradels.ordering.domain.OrderStatus; // Import OrderStatus
import com.yourcompany.agritradels.usermanagement.domain.User;

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
}