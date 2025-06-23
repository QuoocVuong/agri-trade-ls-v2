package com.yourcompany.agritrade.ordering.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.service.ExcelExportService;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.service.OrderService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminOrderController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(roles = {"ADMIN"}) // Tất cả API trong controller này yêu cầu vai trò ADMIN
class AdminOrderControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private OrderService orderService;

  @MockBean private ExcelExportService excelExportService;

  // Authentication sẽ được cung cấp bởi @WithMockUser

  private OrderSummaryResponse orderSummaryResponse;
  private Page<OrderSummaryResponse> orderSummaryPage;
  private OrderResponse orderResponse;
  private OrderStatusUpdateRequest orderStatusUpdateRequest;

  @BeforeEach
  void setUp() {
    orderSummaryResponse = new OrderSummaryResponse();
    orderSummaryResponse.setId(1L);
    orderSummaryResponse.setOrderCode("ADMIN_ORD_001");
    orderSummaryPage = new PageImpl<>(List.of(orderSummaryResponse));

    orderResponse = new OrderResponse();
    orderResponse.setId(1L);
    orderResponse.setOrderCode("ADMIN_ORD_001");
    orderResponse.setStatus(OrderStatus.CONFIRMED); // Trạng thái ban đầu

    orderStatusUpdateRequest = new OrderStatusUpdateRequest();
    orderStatusUpdateRequest.setStatus(OrderStatus.PROCESSING);
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Danh sách Đơn hàng (Admin)")
  class GetAllOrdersTests {
    @Test
    @DisplayName("GET /api/admin/orders - Thành công với bộ lọc")
    void getAllOrders_withFilters_success() throws Exception {
      when(orderService.getAllOrdersForAdmin(
              eq("keyword"),
              eq(OrderStatus.PENDING),
              eq(PaymentMethod.COD),
              eq(PaymentStatus.PENDING),
              eq(OrderType.B2B),
              eq(1L),
              eq(2L),
              any(Pageable.class)))
          .thenReturn(orderSummaryPage);

      mockMvc
          .perform(
              get("/api/admin/orders")
                  .param("keyword", "keyword")
                  .param("status", "PENDING")
                  .param("paymentMethod", "COD")
                  .param("paymentStatus", "PENDING")
                  .param("orderType", "B2B")
                  .param("buyerId", "1")
                  .param("farmerId", "2")
                  .param("page", "0")
                  .param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(
              jsonPath("$.data.content[0].orderCode", is(orderSummaryResponse.getOrderCode())));
    }

    @Test
    @DisplayName("GET /api/admin/orders - Thành công không có bộ lọc")
    void getAllOrders_noFilters_success() throws Exception {
      when(orderService.getAllOrdersForAdmin(
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              any(Pageable.class)))
          .thenReturn(orderSummaryPage);

      mockMvc
          .perform(get("/api/admin/orders"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/admin/orders - Trạng thái không hợp lệ")
    void getAllOrders_invalidStatus_callsServiceWithNullStatus() throws Exception {
      when(orderService.getAllOrdersForAdmin(
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              any(Pageable.class)))
          .thenReturn(orderSummaryPage);

      mockMvc
          .perform(get("/api/admin/orders").param("status", "INVALID_STATUS"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)));

      verify(orderService)
          .getAllOrdersForAdmin(
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              any(Pageable.class));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Chi tiết Đơn hàng (Admin)")
  class GetOrderDetailsForAdminTests {
    @Test
    @DisplayName("GET /api/admin/orders/{orderId} - Thành công")
    void getOrderDetailsForAdmin_success() throws Exception {
      Long orderId = 1L;
      when(orderService.getOrderDetailsForAdmin(eq(orderId))).thenReturn(orderResponse);

      mockMvc
          .perform(get("/api/admin/orders/{orderId}", orderId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.orderCode", is(orderResponse.getOrderCode())));
    }

    @Test
    @DisplayName("GET /api/admin/orders/{orderId} - Đơn hàng không tìm thấy")
    void getOrderDetailsForAdmin_notFound() throws Exception {
      Long orderId = 99L;
      when(orderService.getOrderDetailsForAdmin(eq(orderId)))
          .thenThrow(new ResourceNotFoundException("Order", "id", orderId));

      mockMvc
          .perform(get("/api/admin/orders/{orderId}", orderId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Order not found with id : '99'")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Cập nhật Trạng thái Đơn hàng (Admin)")
  class UpdateOrderStatusByAdminTests {
    @Test
    @DisplayName("PUT /api/admin/orders/{orderId}/status - Thành công")
    void updateOrderStatusByAdmin_success() throws Exception {
      Long orderId = 1L;
      orderResponse.setStatus(OrderStatus.PROCESSING); // Trạng thái mới sau khi cập nhật
      when(orderService.updateOrderStatus(
              any(Authentication.class), eq(orderId), any(OrderStatusUpdateRequest.class)))
          .thenReturn(orderResponse);

      mockMvc
          .perform(
              put("/api/admin/orders/{orderId}/status", orderId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(orderStatusUpdateRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Order status updated successfully by admin")))
          .andExpect(jsonPath("$.data.status", is(OrderStatus.PROCESSING.name())));
    }

    @Test
    @DisplayName("PUT /api/admin/orders/{orderId}/status - Chuyển đổi trạng thái không hợp lệ")
    void updateOrderStatusByAdmin_invalidTransition_throwsBadRequest() throws Exception {
      Long orderId = 1L;
      when(orderService.updateOrderStatus(
              any(Authentication.class), eq(orderId), any(OrderStatusUpdateRequest.class)))
          .thenThrow(new BadRequestException("Invalid status transition."));

      mockMvc
          .perform(
              put("/api/admin/orders/{orderId}/status", orderId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(orderStatusUpdateRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Invalid status transition.")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Hủy Đơn hàng (Admin)")
  @WithMockUser(roles = {"ADMIN"}) // Đảm bảo có quyền
  class CancelOrderByAdminTests {
    @Test
    @DisplayName("POST /api/admin/orders/{orderId}/cancel - Thành công")
    void cancelOrderByAdmin_success() throws Exception {
      Long orderId = 1L;
      orderResponse.setStatus(OrderStatus.CANCELLED); // Trạng thái sau khi hủy
      when(orderService.cancelOrder(any(Authentication.class), eq(orderId)))
          .thenReturn(orderResponse);

      mockMvc
          .perform(post("/api/admin/orders/{orderId}/cancel", orderId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Order cancelled successfully by admin")))
          .andExpect(jsonPath("$.data.status", is(OrderStatus.CANCELLED.name())));
    }

    @Test
    @DisplayName("POST /api/admin/orders/{orderId}/cancel - Không thể hủy")
    void cancelOrderByAdmin_cannotCancel() throws Exception {
      Long orderId = 1L;
      when(orderService.cancelOrder(any(Authentication.class), eq(orderId)))
          .thenThrow(new BadRequestException("Order cannot be cancelled."));

      mockMvc
          .perform(post("/api/admin/orders/{orderId}/cancel", orderId))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Order cannot be cancelled.")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xác nhận Thanh toán Chuyển khoản (Admin)")
  class ConfirmBankTransferTests {
    @Test
    @DisplayName(
        "POST /api/admin/orders/{orderId}/confirm-bank-transfer - Thành công với mã giao dịch")
    void confirmBankTransfer_withTransactionCode_success() throws Exception {
      Long orderId = 1L;
      Map<String, String> payload = new HashMap<>();
      payload.put("bankTransactionCode", "BANK_TX_123");
      orderResponse.setStatus(OrderStatus.CONFIRMED); // Giả sử trạng thái thay đổi

      when(orderService.confirmBankTransferPayment(eq(orderId), eq("BANK_TX_123")))
          .thenReturn(orderResponse);

      mockMvc
          .perform(
              post("/api/admin/orders/{orderId}/confirm-bank-transfer", orderId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(payload)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Bank transfer confirmed.")))
          .andExpect(jsonPath("$.data.status", is(OrderStatus.CONFIRMED.name())));
    }

    @Test
    @DisplayName(
        "POST /api/admin/orders/{orderId}/confirm-bank-transfer - Thành công không có payload")
    void confirmBankTransfer_withoutPayload_success() throws Exception {
      Long orderId = 1L;
      orderResponse.setStatus(OrderStatus.CONFIRMED);
      when(orderService.confirmBankTransferPayment(eq(orderId), isNull()))
          .thenReturn(orderResponse);

      mockMvc
          .perform(
              post("/api/admin/orders/{orderId}/confirm-bank-transfer", orderId)
                  .contentType(MediaType.APPLICATION_JSON)) // Gửi body rỗng
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Bank transfer confirmed.")));
    }

    @Test
    @DisplayName(
        "POST /api/admin/orders/{orderId}/confirm-bank-transfer - Đơn hàng không phải chuyển khoản")
    void confirmBankTransfer_orderNotBankTransfer_throwsBadRequest() throws Exception {
      Long orderId = 1L;
      when(orderService.confirmBankTransferPayment(eq(orderId), any()))
          .thenThrow(new BadRequestException("Order was not placed with Bank Transfer method."));

      mockMvc
          .perform(
              post("/api/admin/orders/{orderId}/confirm-bank-transfer", orderId)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Order was not placed with Bank Transfer method.")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xác nhận Thanh toán Chung (Admin)")
  class ConfirmOrderPaymentTests {
    @Test
    @DisplayName("POST /api/admin/orders/{orderId}/confirm-payment - Thành công")
    void confirmOrderPayment_success() throws Exception {
      Long orderId = 1L;
      PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;
      Map<String, String> payload = new HashMap<>();
      payload.put("notes", "Admin confirmed");
      payload.put("transactionReference", "REF_ADMIN_001");
      orderResponse.setStatus(OrderStatus.CONFIRMED); // Giả sử trạng thái thay đổi

      when(orderService.confirmOrderPaymentByAdmin(
              eq(orderId), eq(paymentMethod), eq("REF_ADMIN_001"), eq("Admin confirmed")))
          .thenReturn(orderResponse);

      mockMvc
          .perform(
              post("/api/admin/orders/{orderId}/confirm-payment", orderId)
                  .param("paymentMethod", paymentMethod.name())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(payload)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(
              jsonPath(
                  "$.message", is("Xác nhận thanh toán cho đơn hàng #" + orderId + " thành công.")))
          .andExpect(jsonPath("$.data.status", is(OrderStatus.CONFIRMED.name())));
    }

    @Test
    @DisplayName("POST /api/admin/orders/{orderId}/confirm-payment - Đơn hàng đã thanh toán")
    void confirmOrderPayment_orderAlreadyPaid_throwsBadRequest() throws Exception {
      Long orderId = 1L;
      PaymentMethod paymentMethod = PaymentMethod.VNPAY;
      when(orderService.confirmOrderPaymentByAdmin(eq(orderId), eq(paymentMethod), any(), any()))
          .thenThrow(new BadRequestException("Đơn hàng này đã được ghi nhận thanh toán."));

      mockMvc
          .perform(
              post("/api/admin/orders/{orderId}/confirm-payment", orderId)
                  .param("paymentMethod", paymentMethod.name())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Đơn hàng này đã được ghi nhận thanh toán.")));
    }
  }
}
