package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request) {
        List<OrderResponse> createdOrders = orderService.checkout(authentication, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(createdOrders, "Order(s) placed successfully"));
    }

    // Lấy lịch sử đơn hàng của người dùng hiện tại (vai trò Buyer)
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getMyOrdersAsBuyer(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "createdAt,desc") Pageable pageable) {
        Page<OrderSummaryResponse> orders = orderService.getMyOrdersAsBuyer(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    // Lấy chi tiết đơn hàng theo ID (người dùng hiện tại phải là buyer hoặc farmer của đơn hàng đó)
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsById(
            Authentication authentication,
            @PathVariable Long orderId) {
        OrderResponse order = orderService.getOrderDetails(authentication, orderId); // Service đã kiểm tra quyền
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    // Lấy chi tiết đơn hàng theo Mã đơn hàng (người dùng hiện tại phải là buyer hoặc farmer)
    @GetMapping("/code/{orderCode}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrderDetailsByCode(
            Authentication authentication,
            @PathVariable String orderCode) {
        OrderResponse order = orderService.getOrderDetailsByCode(authentication, orderCode); // Service đã kiểm tra quyền
        return ResponseEntity.ok(ApiResponse.success(order));
    }


    // Hủy đơn hàng (người dùng hiện tại phải là buyer và đơn hàng ở trạng thái cho phép hủy)
    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelMyOrder(
            Authentication authentication,
            @PathVariable Long orderId) {
        OrderResponse cancelledOrder = orderService.cancelOrder(authentication, orderId); // Service kiểm tra quyền và trạng thái
        return ResponseEntity.ok(ApiResponse.success(cancelledOrder, "Order cancelled successfully"));
    }
}