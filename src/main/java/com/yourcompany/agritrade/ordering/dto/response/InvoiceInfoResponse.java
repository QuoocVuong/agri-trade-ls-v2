// src/main/java/com/yourcompany/agritrade/ordering/dto/response/InvoiceInfoResponse.java
package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceInfoResponse {
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private Long invoiceId;

}