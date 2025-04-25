package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.domain.OrderStatus; // Import Enum
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
import org.springframework.security.core.Authentication; // Import Authentication nếu cần lấy thông tin admin
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders") // Base path cho Admin
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Yêu cầu vai trò Admin
public class AdminOrderController {

    private final OrderService orderService;

    // Lấy tất cả đơn hàng với bộ lọc
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status, // Lọc theo trạng thái Enum
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) Long farmerId,
            @PageableDefault(size = 20, sort = "createdAt,desc") Pageable pageable) {
        Page<OrderSummaryResponse> orders = orderService.getAllOrdersForAdmin(status, buyerId, farmerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    // Lấy chi tiết đơn hàng bất kỳ
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetailsForAdmin(@PathVariable Long orderId) {
        OrderResponse order = orderService.getOrderDetailsForAdmin(orderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    // Admin cập nhật trạng thái đơn hàng
    @PutMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatusByAdmin(
            Authentication authentication, // Có thể cần để ghi log admin nào thực hiện
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse updatedOrder = orderService.updateOrderStatus(authentication, orderId, request); // Service kiểm tra quyền Admin
        return ResponseEntity.ok(ApiResponse.success(updatedOrder, "Order status updated successfully by admin"));
    }

    // Admin hủy đơn hàng (có thể cần quyền riêng)
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL_ALL') or hasRole('ADMIN')") // Ví dụ dùng permission
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrderByAdmin(
            Authentication authentication, // Để biết admin nào hủy
            @PathVariable Long orderId) {
        OrderResponse cancelledOrder = orderService.cancelOrder(authentication, orderId);
        return ResponseEntity.ok(ApiResponse.success(cancelledOrder, "Order cancelled successfully by admin"));
    }
}