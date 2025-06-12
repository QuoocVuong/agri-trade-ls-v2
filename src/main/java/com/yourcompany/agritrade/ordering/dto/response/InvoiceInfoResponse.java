// src/main/java/com/yourcompany/agritrade/ordering/dto/response/InvoiceInfoResponse.java
package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
