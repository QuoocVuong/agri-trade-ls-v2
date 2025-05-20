package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.dto.response.PaymentUrlResponse;

public interface PaymentGatewayService {
  // Trả về URL để redirect người dùng đến cổng thanh toán
  PaymentUrlResponse createVnPayPaymentUrl(Order order, String ipAddress, String returnUrl);

  PaymentUrlResponse createMoMoPaymentUrl(Order order, String returnUrl, String notifyUrl);
  // Thêm các cổng khác nếu cần
}
