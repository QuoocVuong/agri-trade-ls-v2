package com.yourcompany.agritrade.ordering.domain;

public enum PaymentTransactionStatus {
  PENDING,
  SUCCESS,
  FAILED,
  CANCELLED,
  REFUND_REQUESTED, // Đã gửi yêu cầu hoàn tiền đến cổng
  REFUNDED, // Giao dịch đã được hoàn tiền thành công bởi cổng
  REFUND_FAILED // Yêu cầu hoàn tiền bị cổng từ chối/thất bại
}
