package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.service.ExcelExportService;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.service.OrderService;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders") // Base path cho Admin
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Yêu cầu vai trò Admin
public class AdminOrderController {

  private final OrderService orderService;

  @Autowired private ExcelExportService excelExportService;

  // Lấy tất cả đơn hàng với bộ lọc
  @GetMapping
  public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getAllOrders(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) PaymentStatus paymentStatus,
      @RequestParam(required = false) OrderType orderType,
      @RequestParam(required = false) Long buyerId,
      @RequestParam(required = false) Long farmerId,
      @PageableDefault(size = 20, sort = "createdAt,desc") Pageable pageable) {

    OrderStatus statusEnum = null;
    if (StringUtils.hasText(status)) {
      try {
        statusEnum = OrderStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        // Log lại cảnh báo nếu cần, nhưng không ném lỗi

      }
    }
    Page<OrderSummaryResponse> orders =
        orderService.getAllOrdersForAdmin(
            keyword,
            statusEnum,
            paymentMethod,
            paymentStatus,
            orderType,
            buyerId,
            farmerId,
            pageable);
    return ResponseEntity.ok(ApiResponse.success(orders));
  }

  // Lấy chi tiết đơn hàng bất kỳ
  @GetMapping("/{orderId}")
  public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetailsForAdmin(
      @PathVariable Long orderId) {
    OrderResponse order = orderService.getOrderDetailsForAdmin(orderId);
    return ResponseEntity.ok(ApiResponse.success(order));
  }

  // Admin cập nhật trạng thái đơn hàng
  @PutMapping("/{orderId}/status")
  public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatusByAdmin(
      Authentication authentication,
      @PathVariable Long orderId,
      @Valid @RequestBody OrderStatusUpdateRequest request) {
    OrderResponse updatedOrder =
        orderService.updateOrderStatus(
            authentication, orderId, request); // Service kiểm tra quyền Admin
    return ResponseEntity.ok(
        ApiResponse.success(updatedOrder, "Order status updated successfully by admin"));
  }

  // Admin hủy đơn hàng (có thể cần quyền riêng)
  @PostMapping("/{orderId}/cancel")
  @PreAuthorize("hasAuthority('ORDER_CANCEL_ALL') or hasRole('ADMIN')") // Ví dụ dùng permission
  public ResponseEntity<ApiResponse<OrderResponse>> cancelOrderByAdmin(
      Authentication authentication, @PathVariable Long orderId) {
    OrderResponse cancelledOrder = orderService.cancelOrder(authentication, orderId);
    return ResponseEntity.ok(
        ApiResponse.success(cancelledOrder, "Order cancelled successfully by admin"));
  }

  @PostMapping("/{orderId}/confirm-bank-transfer")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ApiResponse<OrderResponse>> confirmBankTransfer(
      @PathVariable Long orderId, @RequestBody(required = false) Map<String, String> payload) {
    String bankTransactionCode = payload != null ? payload.get("bankTransactionCode") : null;
    OrderResponse updatedOrder =
        orderService.confirmBankTransferPayment(orderId, bankTransactionCode);
    return ResponseEntity.ok(ApiResponse.success(updatedOrder, "Bank transfer confirmed."));
  }

  @PostMapping("/{orderId}/confirm-payment")
  public ResponseEntity<ApiResponse<OrderResponse>> confirmOrderPayment(
      @PathVariable Long orderId,
      @RequestParam PaymentMethod paymentMethod, // Admin chọn phương thức đã nhận
      @RequestBody(required = false)
          Map<String, String> payload) { // Có thể nhận ghi chú, mã giao dịch từ Admin
    String adminNotes = payload != null ? payload.get("notes") : null;
    String transactionReference = payload != null ? payload.get("transactionReference") : null;

    OrderResponse updatedOrder =
        orderService.confirmOrderPaymentByAdmin(
            orderId, paymentMethod, transactionReference, adminNotes);
    return ResponseEntity.ok(
        ApiResponse.success(
            updatedOrder, "Xác nhận thanh toán cho đơn hàng #" + orderId + " thành công."));
  }

  @GetMapping("/export")
  public ResponseEntity<InputStreamResource> exportOrdersToExcel(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) PaymentStatus paymentStatus,
      @RequestParam(required = false) Long buyerId,
      @RequestParam(required = false) Long farmerId,
      @RequestParam(required = false) OrderType orderType) {
    List<OrderSummaryResponse> ordersToExport =
        orderService.getAllOrdersForAdminExport(
            keyword, status, paymentMethod, paymentStatus, buyerId, farmerId, orderType);

    try {
      ByteArrayInputStream in = excelExportService.ordersToExcel(ordersToExport);
      String filename = "don_hang_" + LocalDate.now().toString() + ".xlsx";
      InputStreamResource file = new InputStreamResource(in);

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(file);
    } catch (IOException e) {
      throw new RuntimeException("Failed to export data to Excel file: " + e.getMessage());
    }
  }
}
