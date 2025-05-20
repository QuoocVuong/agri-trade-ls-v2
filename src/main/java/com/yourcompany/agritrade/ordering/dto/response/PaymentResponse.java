package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.PaymentTransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PaymentResponse {
  private Long id;
  private String transactionCode;
  private String paymentGateway;
  private BigDecimal amount;
  private PaymentTransactionStatus status;
  private LocalDateTime paymentTime;
  private String gatewayMessage;
  private LocalDateTime createdAt;
}
