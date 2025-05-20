package com.yourcompany.agritrade.ordering.controller; // Đặt trong module ordering

import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.repository.InvoiceRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api") // Base path chung
@RequiredArgsConstructor
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final OrderRepository orderRepository; // Inject để lấy Order
  private final InvoiceRepository invoiceRepository;

  // Endpoint tải hóa đơn theo Order ID
  @GetMapping("/orders/{orderId}/invoice/download")
  @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập
  public ResponseEntity<InputStreamResource> downloadInvoiceByOrderId(@PathVariable Long orderId) {
    // TODO: Thêm kiểm tra quyền truy cập vào orderId này (user là buyer/farmer hoặc admin)

    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

    // Lấy hoặc tạo hóa đơn
    Invoice invoice = invoiceService.getOrCreateInvoiceForOrder(order);

    // Tạo file PDF
    ByteArrayInputStream pdfInputStream = invoiceService.generateInvoicePdf(invoice.getId());

    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf");

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.APPLICATION_PDF)
        .body(new InputStreamResource(pdfInputStream));
  }

  // (Tùy chọn) Endpoint tải hóa đơn theo Invoice ID
  @GetMapping("/invoices/{invoiceId}/download")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<InputStreamResource> downloadInvoiceById(@PathVariable Long invoiceId) {
    // TODO: Kiểm tra quyền truy cập invoiceId
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId) // Cần inject InvoiceRepository
            .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", invoiceId));

    ByteArrayInputStream pdfInputStream = invoiceService.generateInvoicePdf(invoiceId);
    HttpHeaders headers = new HttpHeaders();
    headers.add(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf");
    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.APPLICATION_PDF)
        .body(new InputStreamResource(pdfInputStream));
  }
}
