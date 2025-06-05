package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OrderSummaryResponse {
  private Long id;
  private String orderCode;
  private OrderType orderType;
  private BigDecimal totalAmount;
  private OrderStatus status;
  private PaymentStatus paymentStatus;
  private LocalDateTime createdAt;

  private String buyerName;
  private String farmerName;

}
