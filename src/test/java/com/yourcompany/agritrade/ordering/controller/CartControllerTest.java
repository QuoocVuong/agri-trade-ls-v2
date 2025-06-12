package com.yourcompany.agritrade.ordering.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest;
import com.yourcompany.agritrade.ordering.dto.request.CartItemUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.CartAdjustmentInfo;
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartValidationResponse;
import com.yourcompany.agritrade.ordering.service.CartService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
@Import(TestSecurityConfig.class)
@WithMockUser // Tất cả các API trong CartController đều yêu cầu xác thực
class CartControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private CartService cartService;

  // Không cần mock Authentication ở đây vì @WithMockUser đã cung cấp
  // @Mock private Authentication authentication;

  private CartResponse cartResponse;
  private CartItemRequest cartItemRequest;
  private CartItemUpdateRequest cartItemUpdateRequest;
  private CartItemResponse cartItemResponse;
  private CartValidationResponse cartValidationResponse;

  @BeforeEach
  void setUp() {
    cartItemResponse = new CartItemResponse();
    cartItemResponse.setId(1L);
    cartItemResponse.setQuantity(2);
    // ... các trường khác của CartItemResponse

    cartResponse =
        new CartResponse(
            List.of(cartItemResponse), new BigDecimal("200.00"), 2, Collections.emptyList());

    cartItemRequest = new CartItemRequest();
    cartItemRequest.setProductId(10L);
    cartItemRequest.setQuantity(1);

    cartItemUpdateRequest = new CartItemUpdateRequest();
    cartItemUpdateRequest.setQuantity(3);

    cartValidationResponse =
        new CartValidationResponse(true, List.of("Giỏ hàng hợp lệ"), Collections.emptyList());
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Giỏ hàng")
  class GetMyCartTests {
    @Test
    @DisplayName("GET /api/cart - Thành công")
    void getMyCart_success() throws Exception {
      when(cartService.getCart(any(Authentication.class))).thenReturn(cartResponse);

      mockMvc
          .perform(get("/api/cart"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.totalItems", is(cartResponse.getTotalItems())))
          .andExpect(jsonPath("$.data.items", hasSize(1)));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Thêm Sản phẩm vào Giỏ hàng")
  class AddItemToCartTests {
    @Test
    @DisplayName("POST /api/cart/items - Thành công")
    void addItemToCart_success() throws Exception {
      when(cartService.addItem(any(Authentication.class), any(CartItemRequest.class)))
          .thenReturn(cartItemResponse);

      mockMvc
          .perform(
              post("/api/cart/items")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(cartItemRequest)))
          .andExpect(status().isOk()) // Controller trả về 200 OK
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Item added/updated in cart")))
          .andExpect(jsonPath("$.data.id", is(cartItemResponse.getId().intValue())));
    }

    @Test
    @DisplayName("POST /api/cart/items - Sản phẩm không tồn tại")
    void addItemToCart_productNotFound_throwsBadRequest() throws Exception {
      when(cartService.addItem(any(Authentication.class), any(CartItemRequest.class)))
          .thenThrow(
              new ResourceNotFoundException("Product", "id", cartItemRequest.getProductId()));

      mockMvc
          .perform(
              post("/api/cart/items")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(cartItemRequest)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(
              jsonPath(
                  "$.message",
                  is("Product not found with id : '" + cartItemRequest.getProductId() + "'")));
    }

    @Test
    @DisplayName("POST /api/cart/items - Hết hàng")
    void addItemToCart_outOfStock_throwsBadRequest() throws Exception {
      when(cartService.addItem(any(Authentication.class), any(CartItemRequest.class)))
          .thenThrow(new OutOfStockException("Not enough stock", 5));

      mockMvc
          .perform(
              post("/api/cart/items")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(cartItemRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Not enough stock")))
          .andExpect(jsonPath("$.details.availableStock", is(5)));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Cập nhật Số lượng Sản phẩm trong Giỏ hàng")
  class UpdateCartItemQuantityTests {
    @Test
    @DisplayName("PUT /api/cart/items/{cartItemId} - Thành công")
    void updateCartItemQuantity_success() throws Exception {
      Long cartItemId = 1L;
      when(cartService.updateItemQuantity(
              any(Authentication.class), eq(cartItemId), any(CartItemUpdateRequest.class)))
          .thenReturn(cartItemResponse);

      mockMvc
          .perform(
              put("/api/cart/items/{cartItemId}", cartItemId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(cartItemUpdateRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Cart item quantity updated")))
          .andExpect(jsonPath("$.data.id", is(cartItemResponse.getId().intValue())));
    }

    @Test
    @DisplayName("PUT /api/cart/items/{cartItemId} - Item không tồn tại")
    void updateCartItemQuantity_itemNotFound_throwsNotFound() throws Exception {
      Long cartItemId = 99L;
      when(cartService.updateItemQuantity(
              any(Authentication.class), eq(cartItemId), any(CartItemUpdateRequest.class)))
          .thenThrow(new ResourceNotFoundException("Cart Item", "id", cartItemId));

      mockMvc
          .perform(
              put("/api/cart/items/{cartItemId}", cartItemId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(cartItemUpdateRequest)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Cart Item not found with id : '99'")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xóa Sản phẩm khỏi Giỏ hàng")
  class RemoveCartItemTests {
    @Test
    @DisplayName("DELETE /api/cart/items/{cartItemId} - Thành công")
    void removeCartItem_success() throws Exception {
      Long cartItemId = 1L;
      doNothing().when(cartService).removeItem(any(Authentication.class), eq(cartItemId));

      mockMvc
          .perform(delete("/api/cart/items/{cartItemId}", cartItemId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Cart item removed successfully")));
    }

    @Test
    @DisplayName("DELETE /api/cart/items/{cartItemId} - Item không tồn tại")
    void removeCartItem_itemNotFound_throwsNotFound() throws Exception {
      Long cartItemId = 99L;
      doThrow(new ResourceNotFoundException("Cart Item", "id", cartItemId))
          .when(cartService)
          .removeItem(any(Authentication.class), eq(cartItemId));

      mockMvc
          .perform(delete("/api/cart/items/{cartItemId}", cartItemId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Cart Item not found with id : '99'")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xóa Toàn bộ Giỏ hàng")
  class ClearMyCartTests {
    @Test
    @DisplayName("DELETE /api/cart - Thành công")
    void clearMyCart_success() throws Exception {
      doNothing().when(cartService).clearCart(any(Authentication.class));

      mockMvc
          .perform(delete("/api/cart"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Cart cleared successfully")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xác thực Giỏ hàng")
  class ValidateCartTests {
    @Test
    @DisplayName("POST /api/cart/validate - Giỏ hàng hợp lệ")
    void validateCart_validCart_success() throws Exception {
      when(cartService.validateCartForCheckout(any(Authentication.class)))
          .thenReturn(cartValidationResponse); // cartValidationResponse được setup là hợp lệ

      mockMvc
          .perform(post("/api/cart/validate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.valid", is(true)))
          .andExpect(jsonPath("$.data.messages[0]", is("Giỏ hàng hợp lệ")));
    }

    @Test
    @DisplayName("POST /api/cart/validate - Giỏ hàng không hợp lệ với điều chỉnh")
    void validateCart_invalidCartWithAdjustments_success() throws Exception {
      CartValidationResponse invalidResponse =
          new CartValidationResponse(
              false,
              List.of("Số lượng sản phẩm X đã được cập nhật."),
              List.of(
                  new CartAdjustmentInfo(
                      10L,
                      "Sản phẩm X",
                      "Số lượng đã được cập nhật thành 5 do thay đổi tồn kho.",
                      "ADJUSTED")));
      when(cartService.validateCartForCheckout(any(Authentication.class)))
          .thenReturn(invalidResponse);

      mockMvc
          .perform(post("/api/cart/validate"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.valid", is(false)))
          .andExpect(jsonPath("$.data.messages[0]", is("Số lượng sản phẩm X đã được cập nhật.")))
          .andExpect(jsonPath("$.data.adjustments", hasSize(1)))
          .andExpect(jsonPath("$.data.adjustments[0].type", is("ADJUSTED")));
    }
  }
}
