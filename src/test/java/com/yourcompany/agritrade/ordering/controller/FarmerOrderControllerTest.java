package com.yourcompany.agritrade.ordering.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.service.OrderService;
import java.util.List;
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

@WebMvcTest(FarmerOrderController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(roles = {"FARMER"}) // Tất cả API trong controller này yêu cầu vai trò FARMER
class FarmerOrderControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private OrderService orderService;

  // Authentication sẽ được cung cấp bởi @WithMockUser

  private OrderSummaryResponse orderSummaryResponse;
  private Page<OrderSummaryResponse> orderSummaryPage;
  private OrderResponse orderResponse;
  private OrderStatusUpdateRequest orderStatusUpdateRequest;

  @BeforeEach
  void setUp() {
    orderSummaryResponse = new OrderSummaryResponse();
    orderSummaryResponse.setId(1L);
    orderSummaryResponse.setOrderCode("FARMER_ORD_001");
    orderSummaryPage = new PageImpl<>(List.of(orderSummaryResponse));

    orderResponse = new OrderResponse();
    orderResponse.setId(1L);
    orderResponse.setOrderCode("FARMER_ORD_001");
    orderResponse.setStatus(OrderStatus.PROCESSING);

    orderStatusUpdateRequest = new OrderStatusUpdateRequest();
    orderStatusUpdateRequest.setStatus(OrderStatus.SHIPPING);
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Đơn hàng của Farmer")
  class GetMyOrdersAsFarmerTests {
    @Test
    @DisplayName("GET /api/farmer/orders/my - Thành công với bộ lọc")
    void getMyOrdersAsFarmer_withFilters_success() throws Exception {
      when(orderService.getMyOrdersAsFarmer(
              any(Authentication.class),
              eq("keyword"),
              eq(OrderStatus.PENDING),
              eq(PaymentMethod.COD),
              eq(PaymentStatus.PENDING),
              eq(OrderType.B2B),
              any(Pageable.class)))
          .thenReturn(orderSummaryPage);

      mockMvc
          .perform(
              get("/api/farmer/orders/my")
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
          .andExpect(
              jsonPath("$.data.content[0].orderCode", is(orderSummaryResponse.getOrderCode())));
    }

    @Test
    @DisplayName("GET /api/farmer/orders/my - Thành công không có bộ lọc")
    void getMyOrdersAsFarmer_noFilters_success() throws Exception {
      when(orderService.getMyOrdersAsFarmer(
              any(Authentication.class),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              any(Pageable.class)))
          .thenReturn(orderSummaryPage);

      mockMvc
          .perform(get("/api/farmer/orders/my"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/farmer/orders/my - Trạng thái không hợp lệ")
    void getMyOrdersAsFarmer_invalidStatus_callsServiceWithNullStatus() throws Exception {
      // Service sẽ nhận statusEnum là null nếu chuỗi status không hợp lệ
      when(orderService.getMyOrdersAsFarmer(
              any(Authentication.class),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              any(Pageable.class)))
          .thenReturn(orderSummaryPage);

      mockMvc
          .perform(get("/api/farmer/orders/my").param("status", "INVALID_STATUS"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)));

      verify(orderService)
          .getMyOrdersAsFarmer(
              any(Authentication.class),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              isNull(),
              any(Pageable.class));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Chi tiết Đơn hàng của Farmer")
  class GetMyOrderDetailsAsFarmerTests {
    @Test
    @DisplayName("GET /api/farmer/orders/{orderId} - Thành công")
    void getMyOrderDetailsAsFarmer_success() throws Exception {
      Long orderId = 1L;
      when(orderService.getOrderDetails(any(Authentication.class), eq(orderId)))
          .thenReturn(orderResponse);

      mockMvc
          .perform(get("/api/farmer/orders/{orderId}", orderId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.orderCode", is(orderResponse.getOrderCode())));
    }

    @Test
    @DisplayName("GET /api/farmer/orders/{orderId} - Đơn hàng không tìm thấy")
    void getMyOrderDetailsAsFarmer_notFound() throws Exception {
      Long orderId = 99L;
      when(orderService.getOrderDetails(any(Authentication.class), eq(orderId)))
          .thenThrow(new ResourceNotFoundException("Order", "id", orderId));

      mockMvc
          .perform(get("/api/farmer/orders/{orderId}", orderId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Order not found with id : '99'")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Cập nhật Trạng thái Đơn hàng của Farmer")
  class UpdateMyOrderStatusTests {
    @Test
    @DisplayName("PUT /api/farmer/orders/{orderId}/status - Thành công")
    void updateMyOrderStatus_success() throws Exception {
      Long orderId = 1L;
      orderResponse.setStatus(OrderStatus.SHIPPING); // Giả sử trạng thái mới sau khi cập nhật
      when(orderService.updateOrderStatus(
              any(Authentication.class), eq(orderId), any(OrderStatusUpdateRequest.class)))
          .thenReturn(orderResponse);

      mockMvc
          .perform(
              put("/api/farmer/orders/{orderId}/status", orderId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(orderStatusUpdateRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Order status updated successfully")))
          .andExpect(jsonPath("$.data.status", is(OrderStatus.SHIPPING.name())));
    }

    @Test
    @DisplayName("PUT /api/farmer/orders/{orderId}/status - Chuyển đổi trạng thái không hợp lệ")
    void updateMyOrderStatus_invalidTransition_throwsBadRequest() throws Exception {
      Long orderId = 1L;
      when(orderService.updateOrderStatus(
              any(Authentication.class), eq(orderId), any(OrderStatusUpdateRequest.class)))
          .thenThrow(new BadRequestException("Invalid status transition."));

      mockMvc
          .perform(
              put("/api/farmer/orders/{orderId}/status", orderId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(orderStatusUpdateRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Invalid status transition.")));
    }

    @Test
    @DisplayName("PUT /api/farmer/orders/{orderId}/status - Request không hợp lệ (thiếu status)")
    void updateMyOrderStatus_invalidRequest_throwsBadRequest() throws Exception {
      Long orderId = 1L;
      OrderStatusUpdateRequest invalidRequest = new OrderStatusUpdateRequest(); // status là null

      // Lỗi validation sẽ được Spring xử lý trước khi gọi service
      mockMvc
          .perform(
              put("/api/farmer/orders/{orderId}/status", orderId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(
              jsonPath("$.message", is("Dữ liệu không hợp lệ"))) // Hoặc thông báo lỗi cụ thể từ
          // GlobalExceptionHandler
          .andExpect(jsonPath("$.details.status", is("New status is required")));
    }
  }
}
