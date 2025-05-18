package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.VnPayUtils;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho các API đơn hàng của người dùng
public class OrderController {

    private final @Qualifier("vnPayService") PaymentGatewayService vnPayService;
     private final @Qualifier("moMoService") PaymentGatewayService moMoService; // Nếu có
    @Value("${app.frontend.url}") private String frontendAppUrl; // URL của frontend
    @Value("${app.backend.url}") private String backendAppUrl; // URL của backend (cho IPN)


    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

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

    @PostMapping("/calculate-totals")
    @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
    public ResponseEntity<ApiResponse<OrderCalculationResponse>> calculateOrderTotals(
            Authentication authentication,
            @RequestBody(required = false) OrderCalculationRequest request // DTO chứa thông tin cần thiết (vd: addressId, voucher)
    ) {
        // Nếu request null, có thể lấy thông tin từ giỏ hàng mặc định
        OrderCalculationResponse totals = orderService.calculateOrderTotals(authentication, request);
        return ResponseEntity.ok(ApiResponse.success(totals));
    }

    @PostMapping("/api/orders/{orderId}/create-payment-url")
    @PreAuthorize("hasAnyRole('CONSUMER', 'BUSINESS_BUYER')")
    public ResponseEntity<ApiResponse<PaymentUrlResponse>> createPaymentUrl(
            Authentication authentication,
            @PathVariable Long orderId,
            @RequestParam PaymentMethod paymentMethod, // Frontend gửi lên phương thức muốn dùng
            HttpServletRequest httpServletRequest // Inject HttpServletRequest
    ) {
        User user = getUserFromAuthentication(authentication); // Hàm helper của bạn
        Order order = orderRepository.findByIdAndBuyerId(orderId, user.getId()) // Đảm bảo đúng order của user
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Kiểm tra trạng thái đơn hàng và thanh toán
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Đơn hàng này đã được thanh toán.");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Không thể thanh toán cho đơn hàng đã hủy hoặc đã hoàn thành.");
        }


        PaymentUrlResponse paymentUrlResponse = null;
        String clientIp = VnPayUtils.getIpAddress(httpServletRequest); // Sử dụng VnPayUtils
        String frontendReturnUrl = frontendAppUrl + "/payment/result"; // URL Frontend xử lý sau khi thanh toán


        switch (paymentMethod) {
            case VNPAY:
                frontendReturnUrl = frontendAppUrl + "/payment/vnpay/result"; // Hoặc lấy từ config
                // Cập nhật paymentMethod của Order nếu người dùng chọn lại
                if (order.getPaymentMethod() != PaymentMethod.VNPAY) {
                    order.setPaymentMethod(PaymentMethod.VNPAY);
                    orderRepository.save(order); // Lưu lại
                }
                paymentUrlResponse = vnPayService.createVnPayPaymentUrl(order, clientIp, frontendReturnUrl);
                break;
            case MOMO:
                frontendReturnUrl = frontendAppUrl + "/payment/momo/result"; // Hoặc lấy từ config
                String backendIpnUrl = backendAppUrl + "/api/payments/callback/momo/ipn"; // URL IPN cho MoMo
                if (order.getPaymentMethod() != PaymentMethod.MOMO) {
                    order.setPaymentMethod(PaymentMethod.MOMO);
                    orderRepository.save(order);
                }

                paymentUrlResponse = moMoService.createMoMoPaymentUrl(order, frontendReturnUrl, backendIpnUrl);
                break;
            // Các case khác nếu có
            default:
                throw new BadRequestException("Phương thức thanh toán không được hỗ trợ hoặc không hợp lệ cho việc tạo URL.");
        }

        if (paymentUrlResponse == null || paymentUrlResponse.getPaymentUrl() == null) {
            throw new RuntimeException("Không thể tạo URL thanh toán cho " + paymentMethod.name());
        }

        return ResponseEntity.ok(ApiResponse.success(paymentUrlResponse));
    }

    private String getClientIp(HttpServletRequest request) {
        // Logic lấy IP client (cẩn thận với proxy)
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }

    // ****** SAO CHÉP PHƯƠNG THỨC HELPER VÀO ĐÂY ******
    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            // log.warn("Attempted to get user from null or unauthenticated Authentication object."); // Cần inject Logger nếu muốn log
            throw new AccessDeniedException("User is not authenticated or authentication details are missing.");
        }

        Object principal = authentication.getPrincipal();
        String userIdentifier;

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            userIdentifier = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        } else if (principal instanceof String) {
            userIdentifier = (String) principal;
            if ("anonymousUser".equals(userIdentifier)) {
                // log.warn("Attempted operation by anonymous user.");
                throw new AccessDeniedException("Anonymous user cannot perform this action.");
            }
        } else {
            // log.error("Unexpected principal type: {}", principal.getClass().getName());
            throw new AccessDeniedException("Cannot identify user from authentication principal.");
        }

        return userRepository.findByEmail(userIdentifier)
                .orElseThrow(() -> {
                    // log.error("Authenticated user not found in database with identifier: {}", userIdentifier);
                    return new UsernameNotFoundException("Authenticated user not found: " + userIdentifier);
                });
    }

    @GetMapping("/{orderId}/bank-transfer-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BankTransferInfoResponse>> getBankTransferInfo(
            @PathVariable Long orderId,
            Authentication authentication) {
        BankTransferInfoResponse transferInfo = orderService.getBankTransferInfoForOrder(orderId, authentication);
        return ResponseEntity.ok(ApiResponse.success(transferInfo));
    }
    // **************************************************
}