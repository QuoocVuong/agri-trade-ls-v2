
package com.yourcompany.agritrade.ordering.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderCalculationRequest;
import com.yourcompany.agritrade.ordering.dto.response.*;
import com.yourcompany.agritrade.ordering.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(TestSecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    // Mock HttpServletRequest vì nó được inject vào một số phương thức controller
    @MockBean
    private HttpServletRequest httpServletRequest;


    private CheckoutRequest checkoutRequest;
    private OrderResponse orderResponse;
    private OrderSummaryResponse orderSummaryResponse;
    private Page<OrderSummaryResponse> orderSummaryPage;
    private OrderCalculationRequest orderCalculationRequest;
    private OrderCalculationResponse orderCalculationResponse;
    private PaymentUrlResponse paymentUrlResponse;
    private BankTransferInfoResponse bankTransferInfoResponse;


    @BeforeEach
    void setUp() {
        checkoutRequest = new CheckoutRequest();
        checkoutRequest.setShippingAddressId(1L);
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);

        orderResponse = new OrderResponse();
        orderResponse.setId(1L);
        orderResponse.setOrderCode("ORD123");

        orderSummaryResponse = new OrderSummaryResponse();
        orderSummaryResponse.setId(1L);
        orderSummaryResponse.setOrderCode("ORD123");
        orderSummaryPage = new PageImpl<>(List.of(orderSummaryResponse));

        orderCalculationRequest = new OrderCalculationRequest();
        orderCalculationRequest.setShippingAddressId(1L);

        orderCalculationResponse = new OrderCalculationResponse();
        orderCalculationResponse.setTotalAmount(new BigDecimal("150.00"));

        paymentUrlResponse = new PaymentUrlResponse("http://payment-gateway.com/pay", "VNPAY");

        bankTransferInfoResponse = new BankTransferInfoResponse(
                "NGUYEN VAN A", "123456789", "Vietcombank",
                new BigDecimal("100000"), "ORD123", "CK ORD123", "qr-data-string"
        );
    }

    @Nested
    @DisplayName("Kiểm tra Checkout")
    @WithMockUser(roles = {"CONSUMER"}) // Giả lập người dùng có vai trò CONSUMER
    class CheckoutTests {
        @Test
        @DisplayName("POST /api/orders/checkout - Thành công")
        void checkout_success() throws Exception {
            when(orderService.checkout(any(Authentication.class), any(CheckoutRequest.class)))
                    .thenReturn(List.of(orderResponse));

            mockMvc.perform(post("/api/orders/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(checkoutRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Order(s) placed successfully")))
                    .andExpect(jsonPath("$.data[0].orderCode", is(orderResponse.getOrderCode())));
        }

        @Test
        @DisplayName("POST /api/orders/checkout - Giỏ hàng trống")
        void checkout_emptyCart_throwsBadRequest() throws Exception {
            when(orderService.checkout(any(Authentication.class), any(CheckoutRequest.class)))
                    .thenThrow(new BadRequestException("Cart is empty."));

            mockMvc.perform(post("/api/orders/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(checkoutRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Cart is empty.")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Lấy Đơn hàng của Người mua")
    @WithMockUser(roles = {"CONSUMER"})
    class GetMyOrdersBuyerTests {
        @Test
        @DisplayName("GET /api/orders/my - Thành công với bộ lọc")
        void getMyOrdersAsBuyer_withFilters_success() throws Exception {
            when(orderService.getMyOrdersAsBuyer(any(Authentication.class), eq("keyword"), eq(OrderStatus.PENDING), eq(PaymentMethod.COD), eq(PaymentStatus.PENDING), eq(OrderType.B2B), any(Pageable.class)))
                    .thenReturn(orderSummaryPage);

            mockMvc.perform(get("/api/orders/my")
                            .param("keyword", "keyword")
                            .param("status", "PENDING")
                            .param("paymentMethod", "COD")
                            .param("paymentStatus", "PENDING")
                            .param("orderType", "B2B")
                            .param("page", "0")
                            .param("size", "15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].orderCode", is(orderSummaryResponse.getOrderCode())));
        }

        @Test
        @DisplayName("GET /api/orders/my - Thành công không có bộ lọc")
        void getMyOrdersAsBuyer_noFilters_success() throws Exception {
            when(orderService.getMyOrdersAsBuyer(any(Authentication.class),isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(orderSummaryPage);

            mockMvc.perform(get("/api/orders/my"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Lấy Chi tiết Đơn hàng")
    @WithMockUser // Cần xác thực, quyền cụ thể sẽ được kiểm tra ở service
    class GetOrderDetailsTests {
        @Test
        @DisplayName("GET /api/orders/{orderId} - Thành công")
        void getMyOrderDetailsById_success() throws Exception {
            when(orderService.getOrderDetails(any(Authentication.class), eq(1L))).thenReturn(orderResponse);

            mockMvc.perform(get("/api/orders/{orderId}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.orderCode", is(orderResponse.getOrderCode())));
        }

        @Test
        @DisplayName("GET /api/orders/{orderId} - Không tìm thấy đơn hàng")
        void getMyOrderDetailsById_notFound() throws Exception {
            when(orderService.getOrderDetails(any(Authentication.class), eq(99L)))
                    .thenThrow(new ResourceNotFoundException("Order", "id", 99L));

            mockMvc.perform(get("/api/orders/{orderId}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Order not found with id : '99'")));
        }

        @Test
        @DisplayName("GET /api/orders/code/{orderCode} - Thành công")
        void getMyOrderDetailsByCode_success() throws Exception {
            when(orderService.getOrderDetailsByCode(any(Authentication.class), eq("ORD123"))).thenReturn(orderResponse);

            mockMvc.perform(get("/api/orders/code/{orderCode}", "ORD123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.orderCode", is(orderResponse.getOrderCode())));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Hủy Đơn hàng")
    @WithMockUser(roles = {"CONSUMER"})
    class CancelOrderTests {
        @Test
        @DisplayName("POST /api/orders/{orderId}/cancel - Thành công")
        void cancelMyOrder_success() throws Exception {
            when(orderService.cancelOrder(any(Authentication.class), eq(1L))).thenReturn(orderResponse);

            mockMvc.perform(post("/api/orders/{orderId}/cancel", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Order cancelled successfully")))
                    .andExpect(jsonPath("$.data.orderCode", is(orderResponse.getOrderCode())));
        }

        @Test
        @DisplayName("POST /api/orders/{orderId}/cancel - Không thể hủy")
        void cancelMyOrder_cannotCancel() throws Exception {
            when(orderService.cancelOrder(any(Authentication.class), eq(1L)))
                    .thenThrow(new BadRequestException("Order cannot be cancelled in its current status."));

            mockMvc.perform(post("/api/orders/{orderId}/cancel", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Order cannot be cancelled in its current status.")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Tính toán Tổng đơn hàng")
    @WithMockUser(roles = {"CONSUMER"})
    class CalculateTotalsTests {
        @Test
        @DisplayName("POST /api/orders/calculate-totals - Thành công")
        void calculateOrderTotals_success() throws Exception {
            when(orderService.calculateOrderTotals(any(Authentication.class), any()))
                    .thenReturn(orderCalculationResponse);

            mockMvc.perform(post("/api/orders/calculate-totals")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(orderCalculationRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.totalAmount", is(orderCalculationResponse.getTotalAmount().doubleValue())));
        }

        @Test
        @DisplayName("POST /api/orders/calculate-totals - Request null - Thành công")
        void calculateOrderTotals_nullRequest_success() throws Exception {
            when(orderService.calculateOrderTotals(any(Authentication.class), isNull()))
                    .thenReturn(orderCalculationResponse);

            mockMvc.perform(post("/api/orders/calculate-totals")
                            .contentType(MediaType.APPLICATION_JSON)) // Gửi body rỗng
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.totalAmount", is(orderCalculationResponse.getTotalAmount().doubleValue())));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Tạo URL Thanh toán")
    @WithMockUser(roles = {"CONSUMER"})
    class CreatePaymentUrlTests {
        @Test
        @DisplayName("POST /api/orders/{orderId}/create-payment-url - VNPAY - Thành công")
        void createPaymentUrl_vnPay_success() throws Exception {
            when(orderService.createPaymentUrl(any(Authentication.class), eq(1L), eq(PaymentMethod.VNPAY), any(HttpServletRequest.class)))
                    .thenReturn(paymentUrlResponse);

            mockMvc.perform(post("/api/orders/{orderId}/create-payment-url", 1L)
                            .param("paymentMethod", "VNPAY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.paymentUrl", is(paymentUrlResponse.getPaymentUrl())));
        }

        @Test
        @DisplayName("POST /api/orders/{orderId}/create-payment-url - Phương thức không hỗ trợ")
        void createPaymentUrl_unsupportedMethod_throwsBadRequest() throws Exception {
            when(orderService.createPaymentUrl(any(Authentication.class), eq(1L), eq(PaymentMethod.COD), any(HttpServletRequest.class)))
                    .thenThrow(new BadRequestException("Payment method COD is not supported for URL creation."));

            mockMvc.perform(post("/api/orders/{orderId}/create-payment-url", 1L)
                            .param("paymentMethod", "COD"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Payment method COD is not supported for URL creation.")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Lấy Thông tin Chuyển khoản Ngân hàng")
    @WithMockUser // Cần xác thực
    class GetBankTransferInfoTests {
        @Test
        @DisplayName("GET /api/orders/{orderId}/bank-transfer-info - Thành công")
        void getBankTransferInfo_success() throws Exception {
            when(orderService.getBankTransferInfoForOrder(eq(1L), any(Authentication.class)))
                    .thenReturn(bankTransferInfoResponse);

            mockMvc.perform(get("/api/orders/{orderId}/bank-transfer-info", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.accountNumber", is(bankTransferInfoResponse.getAccountNumber())));
        }

        @Test
        @DisplayName("GET /api/orders/{orderId}/bank-transfer-info - Đơn hàng không phải chuyển khoản")
        void getBankTransferInfo_orderNotBankTransfer_throwsBadRequest() throws Exception {
            when(orderService.getBankTransferInfoForOrder(eq(1L), any(Authentication.class)))
                    .thenThrow(new BadRequestException("Order was not placed with Bank Transfer method."));

            mockMvc.perform(get("/api/orders/{orderId}/bank-transfer-info", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Order was not placed with Bank Transfer method.")));
        }
    }
}
