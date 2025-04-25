package com.yourcompany.agritrade.ordering.dto.request;

import com.yourcompany.agritrade.ordering.domain.PaymentMethod; // Import Enum
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 50, message = "Purchase Order Number cannot exceed 50 characters")
    private String purchaseOrderNumber;
}