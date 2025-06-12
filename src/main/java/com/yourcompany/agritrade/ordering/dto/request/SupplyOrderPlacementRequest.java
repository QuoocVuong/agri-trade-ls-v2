package com.yourcompany.agritrade.ordering.dto.request;

import com.google.firebase.database.annotations.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class SupplyOrderPlacementRequest {
  @NotNull private Long farmerId;
  @NotNull private Long productId; // ID sản phẩm gốc mà buyer đang xem

  @NotNull
  @Min(1)
  private Integer requestedQuantity;

  @NotBlank private String requestedUnit; // Đơn vị Buyer chọn.
  private BigDecimal proposedPricePerUnit; // Số lượng theo requestedUnitcd
  private String buyerNotes; // Giá cho requestedUnit.
  // Thông tin giao hàng (có thể lấy từ địa chỉ mặc định của buyer hoặc cho nhập mới)
  private String shippingFullName;
  private String shippingPhoneNumber;
  private String shippingAddressDetail;
  private String shippingProvinceCode;
  private String shippingDistrictCode;
  private String shippingWardCode;
  private LocalDate expectedDeliveryDate;
}
