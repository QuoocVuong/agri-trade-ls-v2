package com.yourcompany.agritrade.ordering.dto.request;

import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CheckoutRequest {
  @NotNull(message = "Shipping address ID is required")
  private Long shippingAddressId; // ID của địa chỉ đã lưu

  @NotNull(message = "Payment method is required")
  private PaymentMethod paymentMethod;

  @NotNull(message = "Confirmed total amount is required.")
  @Positive(message = "Confirmed total must be positive.")
  private BigDecimal confirmedTotalAmount;

  private String notes; // Ghi chú tùy chọn

  @Size(max = 50, message = "Purchase Order Number cannot exceed 50 characters")
  private String purchaseOrderNumber;
}
