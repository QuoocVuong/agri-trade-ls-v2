package com.yourcompany.agritradels.ordering.dto.response;

import com.yourcompany.agritradels.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritradels.ordering.domain.OrderStatus;
import com.yourcompany.agritradels.ordering.domain.OrderType;
import com.yourcompany.agritradels.ordering.domain.PaymentStatus;
import com.yourcompany.agritradels.usermanagement.dto.response.UserResponse; // Hoặc chỉ cần buyer name/id
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderSummaryResponse {
    private Long id;
    private String orderCode;
    private OrderType orderType;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
    // Có thể thêm thông tin tóm tắt về buyer/farmer nếu cần trong danh sách
    private String buyerName; // Ví dụ
    private String farmerName; // Ví dụ
    // private UserResponse buyerSummary;
    // private FarmerInfoResponse farmerSummary;
}