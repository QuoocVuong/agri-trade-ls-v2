package com.yourcompany.agritradels.ordering.dto.request;

import com.yourcompany.agritradels.ordering.domain.PaymentMethod; // Import Enum
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutRequest {
    @NotNull(message = "Shipping address ID is required")
    private Long shippingAddressId; // ID của địa chỉ đã lưu

    // Hoặc có thể cho phép nhập địa chỉ mới trực tiếp ở đây (cần thêm các trường address)
    // private AddressRequest newShippingAddress;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String notes; // Ghi chú tùy chọn
}