package com.yourcompany.agritrade.ordering.dto.request;

import com.google.firebase.database.annotations.NotNull;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AgreedOrderRequest {
  @NotNull private Long buyerId; // ID người mua (doanh nghiệp/đối tác)

  @NotEmpty private List<AgreedOrderItemRequest> items;

  @NotNull private BigDecimal agreedTotalAmount; // Tổng tiền đã thỏa thuận

  @NotNull private PaymentMethod agreedPaymentMethod; // Phương thức thanh toán đã thỏa thuận

  private Long sourceRequestId; // ID của SupplyOrderRequest gốc

  private String shippingAddressDetail; // Địa chỉ giao hàng thỏa thuận
  private String shippingProvinceCode;
  private String shippingDistrictCode;
  private String shippingWardCode;
  private String shippingFullName;
  private String shippingPhoneNumber;

  private String notes; // Ghi chú thêm
  private LocalDate expectedDeliveryDate; // Ngày giao hàng dự kiến (thỏa thuận)
}
