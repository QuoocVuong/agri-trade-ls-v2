// src/main/java/com/yourcompany/agritrade/ordering/dto/response/InvoiceSummaryResponse.java
package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSummaryResponse {
    private Long invoiceId;
    private String invoiceNumber;
    private Long orderId;
    private String orderCode;
    private String buyerFullName; // Tên người mua (khách hàng)
    private BigDecimal totalAmount;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private LocalDateTime createdAt; // Thời gian tạo hóa đơn
}