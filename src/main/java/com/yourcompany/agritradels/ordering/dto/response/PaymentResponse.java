package com.yourcompany.agritradels.ordering.dto.response;

import com.yourcompany.agritradels.ordering.domain.PaymentTransactionStatus; // Import Enum
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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