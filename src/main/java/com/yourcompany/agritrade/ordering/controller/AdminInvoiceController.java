package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.response.InvoiceSummaryResponse;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvoiceController {

  private final InvoiceService invoiceService; // Tạo service này

  @GetMapping
  public ResponseEntity<ApiResponse<Page<InvoiceSummaryResponse>>> getAllInvoices(
      @RequestParam(required = false) InvoiceStatus status,
      @RequestParam(required = false) PaymentStatus paymentStatus,
      @RequestParam(required = false) String keyword, // Tìm theo mã HĐ, mã ĐH, tên KH
      @PageableDefault(size = 20, sort = "issueDate,desc") Pageable pageable) {
    Page<InvoiceSummaryResponse> invoices =
        invoiceService.getAllInvoicesForAdmin(status, paymentStatus, keyword, pageable);
    return ResponseEntity.ok(ApiResponse.success(invoices));
  }
}
