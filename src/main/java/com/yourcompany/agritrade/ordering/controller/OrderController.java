package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.VnPayUtils;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.request.AgreedOrderRequest;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderCalculationRequest;
import com.yourcompany.agritrade.ordering.dto.response.*;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.service.OrderService;
import com.yourcompany.agritrade.ordering.service.PaymentGatewayService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho các API đơn hàng của người dùng
public class OrderController {


  private final OrderService orderService;

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
      @RequestParam(required = false) String status,
      @PageableDefault(size = 15, sort = "createdAt,desc") Pageable pageable) {

    OrderStatus statusEnum = null;
    if (StringUtils.hasText(status)) {
      try {
        statusEnum = OrderStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {

      }
    }
    Page<OrderSummaryResponse> orders = orderService.getMyOrdersAsBuyer(authentication,keyword,statusEnum, pageable);
    return ResponseEntity.ok(ApiResponse.success(orders));
  }

  // Lấy chi tiết đơn hàng theo ID (người dùng hiện tại phải là buyer hoặc farmer của đơn hàng đó)
  @GetMapping("/{orderId}")
  public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsById(
      Authentication authentication, @PathVariable Long orderId) {
    OrderResponse order =
        orderService.getOrderDetails(authentication, orderId);
    return ResponseEntity.ok(ApiResponse.success(order));
  }

  // Lấy chi tiết đơn hàng theo Mã đơn hàng (người dùng hiện tại phải là buyer hoặc farmer)
  @GetMapping("/code/{orderCode}")
  public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsByCode(
      Authentication authentication, @PathVariable String orderCode) {
    OrderResponse order =
        orderService.getOrderDetailsByCode(authentication, orderCode);
    return ResponseEntity.ok(ApiResponse.success(order));
  }

  // Hủy đơn hàng (người dùng hiện tại phải là buyer và đơn hàng ở trạng thái cho phép hủy)
  @PostMapping("/{orderId}/cancel")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
      Authentication authentication, @PathVariable Long orderId) {
    OrderResponse cancelledOrder =
        orderService.cancelOrder(authentication, orderId);
    return ResponseEntity.ok(ApiResponse.success(cancelledOrder, "Order cancelled successfully"));
  }

  @PostMapping("/calculate-totals")
  @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
  public ResponseEntity<ApiResponse<OrderCalculationResponse>> calculateOrderTotals(
      Authentication authentication,
      @RequestBody(required = false)
          OrderCalculationRequest request
      ) {

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
    PaymentUrlResponse paymentUrlResponse = orderService.createPaymentUrl(authentication, orderId, paymentMethod, httpServletRequest);
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
  @PreAuthorize("hasAnyRole('FARMER')") // Ví dụ: Chỉ Admin hoặc Farmer được tạo
  public ResponseEntity<ApiResponse<OrderResponse>> createAgreedOrder(
          Authentication authentication,
          @Valid @RequestBody AgreedOrderRequest request) {
    OrderResponse createdOrder = orderService.createAgreedOrder(authentication, request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(createdOrder, "Agreed order created successfully."));
  }
}
