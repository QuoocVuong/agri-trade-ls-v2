package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.AgreedOrderRequest;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderCalculationRequest;
import com.yourcompany.agritrade.ordering.dto.request.PaymentNotificationRequest;
import com.yourcompany.agritrade.ordering.dto.response.*;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import com.yourcompany.agritrade.ordering.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho các API đơn hàng của người dùng
public class OrderController {

  private final OrderService orderService;

  private final InvoiceService invoiceService;

  @PostMapping("/checkout")
  // Chỉ Consumer hoặc Business Buyer mới được checkout
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<List<OrderResponse>>> checkout(
      Authentication authentication, @Valid @RequestBody CheckoutRequest request) {
    List<OrderResponse> createdOrders = orderService.checkout(authentication, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(createdOrders, "Order(s) placed successfully"));
  }

  // Lấy lịch sử đơn hàng của người dùng hiện tại (vai trò Buyer)
  @GetMapping("/my")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getMyOrdersAsBuyer(
      Authentication authentication,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) PaymentMethod paymentMethod,
      @RequestParam(required = false) PaymentStatus paymentStatus,
      @RequestParam(required = false) OrderType orderType,
      @PageableDefault(size = 15, sort = "createdAt,desc") Pageable pageable) {

    Page<OrderSummaryResponse> orders =
        orderService.getMyOrdersAsBuyer(
            authentication, keyword, status, paymentMethod, paymentStatus, orderType, pageable);
    return ResponseEntity.ok(ApiResponse.success(orders));
  }

  // Lấy chi tiết đơn hàng theo ID (người dùng hiện tại phải là buyer hoặc farmer của đơn hàng đó)
  @GetMapping("/{orderId}")
  public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsById(
      Authentication authentication, @PathVariable Long orderId) {
    OrderResponse order = orderService.getOrderDetails(authentication, orderId);
    return ResponseEntity.ok(ApiResponse.success(order));
  }

  // Lấy chi tiết đơn hàng theo Mã đơn hàng (người dùng hiện tại phải là buyer hoặc farmer)
  @GetMapping("/code/{orderCode}")
  public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsByCode(
      Authentication authentication, @PathVariable String orderCode) {
    OrderResponse order = orderService.getOrderDetailsByCode(authentication, orderCode);
    return ResponseEntity.ok(ApiResponse.success(order));
  }

  // Hủy đơn hàng (người dùng hiện tại phải là buyer và đơn hàng ở trạng thái cho phép hủy)
  @PostMapping("/{orderId}/cancel")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
      Authentication authentication, @PathVariable Long orderId) {
    OrderResponse cancelledOrder = orderService.cancelOrder(authentication, orderId);
    return ResponseEntity.ok(ApiResponse.success(cancelledOrder, "Order cancelled successfully"));
  }

  @PostMapping("/calculate-totals")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<OrderCalculationResponse>> calculateOrderTotals(
      Authentication authentication,
      @RequestBody(required = false) OrderCalculationRequest request) {

    OrderCalculationResponse totals = orderService.calculateOrderTotals(authentication, request);
    return ResponseEntity.ok(ApiResponse.success(totals));
  }

  @PostMapping("/{orderId}/create-payment-url")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<PaymentUrlResponse>> createPaymentUrl(
      Authentication authentication,
      @PathVariable Long orderId,
      @RequestParam PaymentMethod paymentMethod,
      HttpServletRequest httpServletRequest) {
    // Gọi phương thức mới trong OrderService
    PaymentUrlResponse paymentUrlResponse =
        orderService.createPaymentUrl(authentication, orderId, paymentMethod, httpServletRequest);
    return ResponseEntity.ok(ApiResponse.success(paymentUrlResponse));
  }

  @GetMapping("/{orderId}/bank-transfer-info")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<BankTransferInfoResponse>> getBankTransferInfo(
      @PathVariable Long orderId, Authentication authentication) {
    BankTransferInfoResponse transferInfo =
        orderService.getBankTransferInfoForOrder(orderId, authentication);
    return ResponseEntity.ok(ApiResponse.success(transferInfo));
  }

  @PostMapping("/agreed-order")
  @PreAuthorize("hasAnyRole('FARMER')") // Ví dụ: Farmer được tạo
  public ResponseEntity<ApiResponse<OrderResponse>> createAgreedOrder(
      Authentication authentication, @Valid @RequestBody AgreedOrderRequest request) {
    OrderResponse createdOrder = orderService.createAgreedOrder(authentication, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(createdOrder, "Agreed order created successfully."));
  }

  @GetMapping("/my/invoices")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')") // Cho  Consumer và Business Buyer
  public ResponseEntity<ApiResponse<Page<InvoiceSummaryResponse>>> getMyDebtInvoices(
      Authentication authentication,
      @RequestParam(required = false)
          InvoiceStatus status, // Buyer có thể muốn lọc theo trạng thái hóa đơn
      @RequestParam(required = false) PaymentStatus paymentStatus,
      @RequestParam(required = false) String keyword, // Tìm theo mã HĐ, mã ĐH
      @PageableDefault(size = 15, sort = "dueDate,asc")
          Pageable pageable) { // Sắp xếp theo ngày đáo hạn gần nhất
    Page<InvoiceSummaryResponse> invoices =
        invoiceService.getInvoicesForBuyer(
            authentication, status, paymentStatus, keyword, pageable);
    return ResponseEntity.ok(ApiResponse.success(invoices));
  }

  @PostMapping("/{orderId}/notify-payment-made")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<Void>> notifyPaymentMade(
      @PathVariable Long orderId,
      @RequestBody(required = false) PaymentNotificationRequest request, // Tạo DTO này
      Authentication authentication) {
    orderService.processBuyerPaymentNotification(orderId, request, authentication);
    return ResponseEntity.ok(
        ApiResponse.success("Payment notification received. Admin/Farmer will verify soon."));
  }
}
