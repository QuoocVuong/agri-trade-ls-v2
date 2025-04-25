package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.dto.request.PaymentCallbackRequest; // DTO cho callback

public interface PaymentService {

    /** Xử lý thông tin callback từ cổng thanh toán (Webhook/IPN) */
    void handlePaymentCallback(String gateway, PaymentCallbackRequest callbackData);

    // Có thể thêm các phương thức khác liên quan đến thanh toán nếu cần
}