package com.yourcompany.agritrade.ordering.dto.request;

import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusUpdateRequest {
  @NotNull(message = "New status is required")
  private OrderStatus status;

  private String notes; // Ghi chú thêm của người cập nhật (Admin/Farmer)
}
