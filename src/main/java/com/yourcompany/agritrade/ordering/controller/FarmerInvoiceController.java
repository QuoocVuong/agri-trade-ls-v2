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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farmer/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARMER')")
public class FarmerInvoiceController {
    private final InvoiceService invoiceService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<InvoiceSummaryResponse>>> getMyInvoices(
            Authentication authentication,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 15, sort = "issueDate,desc") Pageable pageable) {
        Page<InvoiceSummaryResponse> invoices = invoiceService.getInvoicesForFarmer(authentication, status, paymentStatus, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }
}