package com.yourcompany.agritrade.interaction.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.interaction.service.FavoriteService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FavoriteController.class)
@Import(TestSecurityConfig.class)
@WithMockUser // Tất cả API trong controller này yêu cầu xác thực
class FavoriteControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private FavoriteService favoriteService;

  // Authentication sẽ được cung cấp bởi @WithMockUser

  private ProductSummaryResponse productSummaryResponse;
  private Page<ProductSummaryResponse> productSummaryPage;

  @BeforeEach
  void setUp() {
    productSummaryResponse = new ProductSummaryResponse();
    productSummaryResponse.setId(1L);
    productSummaryResponse.setName("Sản phẩm Yêu thích Mẫu");
    productSummaryResponse.setPrice(new BigDecimal("120000"));

    productSummaryPage = new PageImpl<>(List.of(productSummaryResponse));
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Danh sách Yêu thích")
  class GetMyFavoritesTests {
    @Test
    @DisplayName("GET /api/favorites/my - Thành công")
    void getMyFavorites_success() throws Exception {
      when(favoriteService.getMyFavorites(any(Authentication.class), any(Pageable.class)))
          .thenReturn(productSummaryPage);

      mockMvc
          .perform(get("/api/favorites/my").param("page", "0").param("size", "12"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));
    }

    @Test
    @DisplayName("GET /api/favorites/my - Không có sản phẩm yêu thích")
    void getMyFavorites_emptyList() throws Exception {
      Page<ProductSummaryResponse> emptyPage = new PageImpl<>(Collections.emptyList());
      when(favoriteService.getMyFavorites(any(Authentication.class), any(Pageable.class)))
          .thenReturn(emptyPage);

      mockMvc
          .perform(get("/api/favorites/my"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(0)));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Thêm vào Yêu thích")
  class AddFavoriteTests {
    @Test
    @DisplayName("POST /api/favorites/product/{productId} - Thành công")
    void addFavorite_success() throws Exception {
      Long productId = 1L;
      doNothing().when(favoriteService).addFavorite(any(Authentication.class), eq(productId));

      mockMvc
          .perform(post("/api/favorites/product/{productId}", productId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product added to favorites")));
    }

    @Test
    @DisplayName("POST /api/favorites/product/{productId} - Sản phẩm không tồn tại")
    void addFavorite_productNotFound_throwsNotFound() throws Exception {
      Long productId = 99L;
      doThrow(new ResourceNotFoundException("Product", "id", productId))
          .when(favoriteService)
          .addFavorite(any(Authentication.class), eq(productId));

      mockMvc
          .perform(post("/api/favorites/product/{productId}", productId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Product not found with id : '99'")));
    }

    @Test
    @DisplayName("POST /api/favorites/product/{productId} - Sản phẩm đã có trong yêu thích")
    void addFavorite_alreadyFavorited_serviceHandlesGracefully() throws Exception {
      // Giả sử service không ném lỗi mà chỉ không làm gì nếu đã yêu thích
      Long productId = 1L;
      // Không cần mock doNothing() vì service có thể không làm gì
      // doNothing().when(favoriteService).addFavorite(any(Authentication.class), eq(productId));
      // Hoặc nếu service ném BadRequestException:
      // doThrow(new BadRequestException("Product already in favorites."))
      //        .when(favoriteService).addFavorite(any(Authentication.class), eq(productId));

      mockMvc
          .perform(post("/api/favorites/product/{productId}", productId))
          .andExpect(status().isOk()) // Mong đợi 200 OK vì controller không bắt lỗi này
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product added to favorites")));
      // Test này giả định service không ném lỗi khi thêm sản phẩm đã yêu thích.
      // Nếu service có ném lỗi, bạn cần điều chỉnh .andExpect(status().isBadRequest())
    }
  }

  @Nested
  @DisplayName("Kiểm tra Xóa khỏi Yêu thích")
  class RemoveFavoriteTests {
    @Test
    @DisplayName("DELETE /api/favorites/product/{productId} - Thành công")
    void removeFavorite_success() throws Exception {
      Long productId = 1L;
      doNothing().when(favoriteService).removeFavorite(any(Authentication.class), eq(productId));

      mockMvc
          .perform(delete("/api/favorites/product/{productId}", productId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product removed from favorites")));
    }

    @Test
    @DisplayName("DELETE /api/favorites/product/{productId} - Sản phẩm không có trong yêu thích")
    void removeFavorite_notFavorited_serviceHandlesGracefully() throws Exception {
      Long productId = 99L;
      // Giả sử service không ném lỗi nếu sản phẩm không có trong danh sách yêu thích
      // doNothing().when(favoriteService).removeFavorite(any(Authentication.class), eq(productId));
      // Hoặc nếu service ném ResourceNotFoundException:
      // doThrow(new ResourceNotFoundException("Favorite", "productId", productId))
      //        .when(favoriteService).removeFavorite(any(Authentication.class), eq(productId));

      mockMvc
          .perform(delete("/api/favorites/product/{productId}", productId))
          .andExpect(status().isOk()) // Mong đợi 200 OK
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product removed from favorites")));
      // Test này giả định service không ném lỗi.
    }
  }

  @Nested
  @DisplayName("Kiểm tra Trạng thái Yêu thích")
  class CheckFavoriteStatusTests {
    @Test
    @DisplayName("GET /api/favorites/product/{productId}/status - Sản phẩm được yêu thích")
    void checkFavoriteStatus_isFavorite_returnsTrue() throws Exception {
      Long productId = 1L;
      when(favoriteService.isFavorite(any(Authentication.class), eq(productId))).thenReturn(true);

      mockMvc
          .perform(get("/api/favorites/product/{productId}/status", productId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data", is(true)));
    }

    @Test
    @DisplayName("GET /api/favorites/product/{productId}/status - Sản phẩm không được yêu thích")
    void checkFavoriteStatus_isNotFavorite_returnsFalse() throws Exception {
      Long productId = 2L;
      when(favoriteService.isFavorite(any(Authentication.class), eq(productId))).thenReturn(false);

      mockMvc
          .perform(get("/api/favorites/product/{productId}/status", productId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data", is(false)));
    }
  }
}
