package com.yourcompany.agritrade.ordering.dto.request;

import java.math.BigDecimal;
import lombok.Data;

// Cấu trúc này sẽ rất khác nhau tùy cổng thanh toán
@Data
public class PaymentCallbackRequest {
  private String orderCode; // Mã đơn hàng của hệ thống mình
  private String transactionCode; // Mã giao dịch của cổng thanh toán
  private boolean success; // Trạng thái thành công/thất bại
  private BigDecimal amount; // Số tiền giao dịch
  private String signature; // Chữ ký để xác thực (ví dụ)
  private String errorMessage; // Thông báo lỗi nếu thất bại
}
