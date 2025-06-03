package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.dto.response.PaymentUrlResponse;

import java.math.BigDecimal;

public interface PaymentGatewayService {
  // Trả về URL để redirect người dùng đến cổng thanh toán
  PaymentUrlResponse createVnPayPaymentUrl(Order order, String ipAddress, String returnUrl);

  PaymentUrlResponse createMoMoPaymentUrl(Order order, String returnUrl, String notifyUrl);
  // Thêm các cổng khác nếu cần

  /**
   * Gửi yêu cầu hoàn tiền đến cổng thanh toán.
   *
   * @param originalTransactionCode Mã giao dịch gốc cần hoàn tiền.
   * @param refundAmount Số tiền cần hoàn.
   * @param reason Lý do hoàn tiền (có thể là mã đơn hàng bị hủy).
   * @return true nếu yêu cầu hoàn tiền được gửi thành công đến cổng,
   *         false nếu có lỗi xảy ra trong quá trình gửi yêu cầu.
   *         Lưu ý: true không có nghĩa là tiền đã được hoàn ngay lập tức.
   */
  boolean requestRefund(String originalTransactionCode, BigDecimal refundAmount, String reason);
  // Thêm các cổng khác nếu cần
}
