package com.yourcompany.agritradels.ordering.dto.request;

import com.yourcompany.agritradels.ordering.domain.OrderStatus; // Import Enum
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusUpdateRequest {
    @NotNull(message = "New status is required")
    private OrderStatus status;
    private String notes; // Ghi chú thêm của người cập nhật (Admin/Farmer)
}