package com.yourcompany.agritrade.ordering.domain;

public enum SupplyOrderRequestStatus {
  PENDING_FARMER_ACTION, // Chờ Farmer phản hồi
  FARMER_ACCEPTED, // Farmer đã chấp nhận (và có thể đã tạo Order)
  FARMER_REJECTED, // Farmer đã từ chối
  BUYER_CANCELLED, // Buyer hủy yêu cầu (nếu cho phép)
  NEGOTIATING // Đang trong quá trình thương lượng (ví dụ: qua chat)
}
