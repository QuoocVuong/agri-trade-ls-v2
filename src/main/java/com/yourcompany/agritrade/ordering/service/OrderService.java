package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.domain.OrderStatus; // Import Enum
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest; // Import DTO
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import java.util.List; // Import List

public interface OrderService {

    /** Tạo một hoặc nhiều đơn hàng từ giỏ hàng của user */
    List<OrderResponse> checkout(Authentication authentication, CheckoutRequest request);

    /** Lấy danh sách đơn hàng của người mua hiện tại (phân trang) */
    Page<OrderSummaryResponse> getMyOrdersAsBuyer(Authentication authentication, Pageable pageable);

    /** Lấy danh sách đơn hàng của nông dân hiện tại (phân trang) */
    Page<OrderSummaryResponse> getMyOrdersAsFarmer(Authentication authentication, Pageable pageable);

    /** Lấy danh sách tất cả đơn hàng cho Admin (phân trang, có filter) */
    Page<OrderSummaryResponse> getAllOrdersForAdmin(OrderStatus status, Long buyerId, Long farmerId, Pageable pageable);

    /** Lấy chi tiết đơn hàng theo ID (kiểm tra quyền truy cập) */
    OrderResponse getOrderDetails(Authentication authentication, Long orderId);

    /** Lấy chi tiết đơn hàng theo Mã đơn hàng (kiểm tra quyền truy cập) */
    OrderResponse getOrderDetailsByCode(Authentication authentication, String orderCode);

    /** Lấy chi tiết đơn hàng theo ID cho Admin (không cần kiểm tra quyền) */
    OrderResponse getOrderDetailsForAdmin(Long orderId);

    /** Cập nhật trạng thái đơn hàng (cho Farmer hoặc Admin) */
    OrderResponse updateOrderStatus(Authentication authentication, Long orderId, OrderStatusUpdateRequest request);

    /** Hủy đơn hàng (cho Buyer hoặc Admin, tùy trạng thái) */
    OrderResponse cancelOrder(Authentication authentication, Long orderId);

}