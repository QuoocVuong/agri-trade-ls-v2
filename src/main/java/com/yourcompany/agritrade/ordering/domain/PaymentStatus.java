package com.yourcompany.agritrade.ordering.domain;

public enum PaymentStatus {
  PENDING,                // Chờ thanh toán
  PAID,                   // Đã thanh toán
  FAILED,                 // Thanh toán thất bại
  REFUND_PENDING,         // Đang chờ xử lý hoàn tiền (đã yêu cầu hoặc đang trong quá trình)
  REFUNDED,               // Đã hoàn tiền thành công
  REFUND_MANUAL_REQUIRED, // Cần xử lý hoàn tiền thủ công
  AWAITING_PAYMENT_TERM   // Chờ thanh toán theo điều khoản (ví dụ: công nợ)
}
