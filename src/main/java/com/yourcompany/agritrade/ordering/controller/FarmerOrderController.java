package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farmer/orders") // Base path cho Farmer
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARMER')") // Yêu cầu vai trò Farmer
public class FarmerOrderController {

  private final OrderService orderService;

  // Lấy danh sách đơn hàng MÀ MÌNH LÀ NGƯỜI BÁN
  @GetMapping("/my")
  public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getMyOrdersAsFarmer(
          Authentication authentication,
          @RequestParam(required = false) String keyword,
          @RequestParam(required = false) OrderStatus status,
          @RequestParam(required = false) PaymentMethod paymentMethod,
          @RequestParam(required = false) PaymentStatus paymentStatus,
          @PageableDefault(size = 15, sort = "createdAt,desc") Pageable pageable) {


    Page<OrderSummaryResponse> orders =
        orderService.getMyOrdersAsFarmer(authentication, keyword, status, paymentMethod, paymentStatus, pageable);
    return ResponseEntity.ok(ApiResponse.success(orders));
  }

  // Lấy chi tiết đơn hàng MÀ MÌNH LÀ NGƯỜI BÁN
  @GetMapping("/{orderId}")
  public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsAsFarmer(
      Authentication authentication, @PathVariable Long orderId) {

    OrderResponse order = orderService.getOrderDetails(authentication, orderId);
    return ResponseEntity.ok(ApiResponse.success(order));
  }

  // Farmer cập nhật trạng thái đơn hàng của mình
  @PutMapping("/{orderId}/status")
  public ResponseEntity<ApiResponse<OrderResponse>> updateMyOrderStatus(
      Authentication authentication,
      @PathVariable Long orderId,
      @Valid @RequestBody OrderStatusUpdateRequest request) {
    OrderResponse updatedOrder =
        orderService.updateOrderStatus(
            authentication, orderId, request);
    return ResponseEntity.ok(
        ApiResponse.success(updatedOrder, "Order status updated successfully"));
  }
}
