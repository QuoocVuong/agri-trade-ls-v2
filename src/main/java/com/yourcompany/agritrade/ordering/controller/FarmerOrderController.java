package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
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
      @RequestParam(required = false) String keyword, // THÊM
      @RequestParam(required = false) String status, // THÊM (nhận là String)
      @PageableDefault(size = 15, sort = "createdAt,desc") Pageable pageable) {

    OrderStatus statusEnum = null;
    if (StringUtils.hasText(status)) {
      try {
        statusEnum = OrderStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        // log.warn("Invalid order status value received: {}", status);
        // Có thể trả về lỗi BadRequest hoặc bỏ qua filter này
      }
    }

    Page<OrderSummaryResponse> orders =
        orderService.getMyOrdersAsFarmer(authentication, keyword, statusEnum, pageable);
    return ResponseEntity.ok(ApiResponse.success(orders));
  }

  // Lấy chi tiết đơn hàng MÀ MÌNH LÀ NGƯỜI BÁN
  @GetMapping("/{orderId}")
  public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsAsFarmer(
      Authentication authentication, @PathVariable Long orderId) {
    // Có thể gọi getOrderDetails và để service kiểm tra quyền,
    // hoặc tạo phương thức riêng trong service chỉ lấy đơn hàng của farmer đó
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
            authentication, orderId, request); // Service kiểm tra quyền và logic trạng thái
    return ResponseEntity.ok(
        ApiResponse.success(updatedOrder, "Order status updated successfully"));
  }
}
