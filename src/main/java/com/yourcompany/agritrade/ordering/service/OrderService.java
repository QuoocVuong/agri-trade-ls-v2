package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.dto.request.AgreedOrderRequest;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderCalculationRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.*;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface OrderService {

  /** Tạo một hoặc nhiều đơn hàng từ giỏ hàng của user */
  List<OrderResponse> checkout(Authentication authentication, CheckoutRequest request);

  /** Lấy danh sách đơn hàng của người mua hiện tại (phân trang) */
  Page<OrderSummaryResponse> getMyOrdersAsBuyer(Authentication authentication, String keyword, OrderStatus status, Pageable pageable);

  /** Lấy danh sách đơn hàng của nông dân hiện tại (phân trang) */
  Page<OrderSummaryResponse> getMyOrdersAsFarmer(
      Authentication authentication, String keyword, OrderStatus status, Pageable pageable);

  /** Lấy danh sách tất cả đơn hàng cho Admin (phân trang, có filter) */
  Page<OrderSummaryResponse> getAllOrdersForAdmin(
      String keyword, OrderStatus status, Long buyerId, Long farmerId, Pageable pageable);

  /** Lấy chi tiết đơn hàng theo ID (kiểm tra quyền truy cập) */
  OrderResponse getOrderDetails(Authentication authentication, Long orderId);

  /** Lấy chi tiết đơn hàng theo Mã đơn hàng (kiểm tra quyền truy cập) */
  OrderResponse getOrderDetailsByCode(Authentication authentication, String orderCode);

  /** Lấy chi tiết đơn hàng theo ID cho Admin (không cần kiểm tra quyền) */
  OrderResponse getOrderDetailsForAdmin(Long orderId);

  /** Cập nhật trạng thái đơn hàng (cho Farmer hoặc Admin) */
  OrderResponse updateOrderStatus(
      Authentication authentication, Long orderId, OrderStatusUpdateRequest request);

  /** Hủy đơn hàng (cho Buyer hoặc Admin, tùy trạng thái) */
  OrderResponse cancelOrder(Authentication authentication, Long orderId);

  OrderCalculationResponse calculateOrderTotals(
      Authentication authentication, OrderCalculationRequest request);

  OrderResponse confirmBankTransferPayment(Long orderId, String bankTransactionCode);

  BankTransferInfoResponse getBankTransferInfoForOrder(Long orderId, Authentication authentication);

  OrderResponse confirmOrderPaymentByAdmin(
      Long orderId,
      PaymentMethod paymentMethodConfirmed,
      String transactionReference,
      String adminNotes);

  String generateOrderCode();

  PaymentUrlResponse createPaymentUrl(Authentication authentication, Long orderId, PaymentMethod paymentMethod, HttpServletRequest httpServletRequest);

  OrderResponse createAgreedOrder(Authentication authentication, AgreedOrderRequest request);
}
