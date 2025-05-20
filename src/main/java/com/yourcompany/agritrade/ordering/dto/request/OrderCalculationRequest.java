package com.yourcompany.agritrade.ordering.dto.request;

import lombok.Data;

@Data
public class OrderCalculationRequest {
  private Long shippingAddressId; // ID địa chỉ để tính phí ship
  // private String voucherCode; // Nếu có voucher
  // private List<CartItemInfo> items; // Hoặc gửi danh sách item nếu không muốn lấy từ giỏ hàng DB
}
