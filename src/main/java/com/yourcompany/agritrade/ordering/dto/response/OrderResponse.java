package com.yourcompany.agritrade.ordering.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yourcompany.agritrade.catalog.dto.response.FarmerInfoResponse;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.usermanagement.dto.response.UserResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {
  private Long id;
  private String orderCode;
  private OrderType orderType;

  // Thông tin người mua và người bán
  private UserResponse buyer; // Thông tin cơ bản người mua
  private FarmerInfoResponse farmer; // Thông tin cơ bản người bán

  // Thông tin giao hàng
  private String shippingFullName;
  private String shippingPhoneNumber;
  private String shippingAddressDetail;
  private String shippingProvinceCode;
  private String shippingDistrictCode;
  private String shippingWardCode;

  // Giá trị
  private BigDecimal subTotal;
  private BigDecimal shippingFee;
  private BigDecimal discountAmount;
  private BigDecimal totalAmount;

  // Thanh toán & Trạng thái
  private PaymentMethod paymentMethod;
  private PaymentStatus paymentStatus;
  private OrderStatus status;
  private String notes;
  private String purchaseOrderNumber;

  // Chi tiết
  private List<OrderItemResponse> orderItems;
  private List<PaymentResponse> payments; // Lịch sử giao dịch

  private InvoiceInfoResponse invoiceInfo;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
