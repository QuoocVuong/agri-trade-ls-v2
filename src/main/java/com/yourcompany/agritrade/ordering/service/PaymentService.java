package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.dto.request.PaymentCallbackRequest;

public interface PaymentService {

  /** Xử lý thông tin callback từ cổng thanh toán (Webhook/IPN) */
  void handlePaymentCallback(String gateway, PaymentCallbackRequest callbackData);
}
