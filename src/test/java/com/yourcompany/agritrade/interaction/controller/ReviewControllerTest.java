package com.yourcompany.agritrade.interaction.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import java.time.LocalDateTime;
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

@WebMvcTest(ReviewController.class)
@Import(TestSecurityConfig.class)
// Không đặt @WithMockUser ở cấp lớp vì có cả API public và API cần quyền khác nhau
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private ReviewService reviewService;

  // Authentication sẽ được cung cấp bởi @WithMockUser trong từng test case nếu cần

  private ReviewRequest reviewRequest;
  private ReviewResponse reviewResponse;
  private Page<ReviewResponse> reviewResponsePage;

  @BeforeEach
  void setUp() {
    reviewRequest = new ReviewRequest();
    reviewRequest.setProductId(1L);
    reviewRequest.setRating(5);
    reviewRequest.setComment("Sản phẩm tuyệt vời!");

    reviewResponse = new ReviewResponse();
    reviewResponse.setId(1L);
    reviewResponse.setRating(5);
    reviewResponse.setComment("Sản phẩm tuyệt vời!");
    reviewResponse.setCreatedAt(LocalDateTime.now());
    // ... các trường khác của ReviewResponse

    reviewResponsePage = new PageImpl<>(List.of(reviewResponse));
  }

  @Nested
  @DisplayName("Kiểm tra Tạo Đánh giá")
  class CreateReviewTests {
    @Test
    @WithMockUser(roles = {"CONSUMER"}) // Giả lập người dùng có vai trò CONSUMER
    @DisplayName("POST /api/reviews - Tạo Đánh giá - Thành công")
    void createReview_success() throws Exception {
      when(reviewService.createReview(any(Authentication.class), any(ReviewRequest.class)))
          .thenReturn(reviewResponse);

      mockMvc
          .perform(
              post("/api/reviews")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(reviewRequest)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Review submitted successfully.")))
          .andExpect(jsonPath("$.data.comment", is(reviewResponse.getComment())));
    }

    @Test
    @WithMockUser(roles = {"FARMER"}) // Farmer không được tạo review
    @DisplayName("POST /api/reviews - Tạo Đánh giá - Bị cấm (Không đúng vai trò)")
    void createReview_forbidden_wrongRole() throws Exception {
      // Không cần mock reviewService.createReview vì sẽ bị chặn bởi @PreAuthorize

      mockMvc
          .perform(
              post("/api/reviews")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(reviewRequest)))
          .andExpect(status().isForbidden()); // Mong đợi lỗi 403
    }

    @Test
    @WithMockUser(roles = {"CONSUMER"})
    @DisplayName("POST /api/reviews - Tạo Đánh giá - Request không hợp lệ")
    void createReview_invalidRequest_throwsBadRequest() throws Exception {
      ReviewRequest invalidRequest = new ReviewRequest(); // Thiếu productId và rating

      mockMvc
          .perform(
              post("/api/reviews")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Validation Failed")))
          .andExpect(jsonPath("$.details.productId", is("Product ID is required")))
          .andExpect(jsonPath("$.details.rating", is("Rating is required")));
    }

    @Test
    @WithMockUser(roles = {"CONSUMER"})
    @DisplayName("POST /api/reviews - Tạo Đánh giá - Người dùng chưa mua sản phẩm")
    void createReview_userHasNotPurchased_throwsBadRequest() throws Exception {
      when(reviewService.createReview(any(Authentication.class), any(ReviewRequest.class)))
          .thenThrow(new BadRequestException("You must purchase this product to leave a review."));

      mockMvc
          .perform(
              post("/api/reviews")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(reviewRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(
              jsonPath("$.message", is("You must purchase this product to leave a review.")));
    }
  }

  @Nested
  @DisplayName("Kiểm tra Lấy Đánh giá")
  class GetReviewsTests {
    @Test
    @DisplayName(
        "GET /api/reviews/product/{productId} - Lấy Đánh giá Đã duyệt của Sản phẩm - Thành công")
    void getApprovedReviewsByProduct_success() throws Exception {
      Long productId = 1L;
      when(reviewService.getApprovedReviewsByProduct(eq(productId), any(Pageable.class)))
          .thenReturn(reviewResponsePage);

      mockMvc
          .perform(
              get("/api/reviews/product/{productId}", productId)
                  .param("page", "0")
                  .param("size", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].comment", is(reviewResponse.getComment())));
    }

    @Test
    @DisplayName("GET /api/reviews/product/{productId} - Sản phẩm không tồn tại")
    void getApprovedReviewsByProduct_productNotFound() throws Exception {
      Long productId = 99L;
      when(reviewService.getApprovedReviewsByProduct(eq(productId), any(Pageable.class)))
          .thenThrow(new ResourceNotFoundException("Product", "id", productId));

      mockMvc
          .perform(get("/api/reviews/product/{productId}", productId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Product not found with id : '99'")));
    }

    @Test
    @WithMockUser // Yêu cầu đã đăng nhập
    @DisplayName("GET /api/reviews/my - Lấy Đánh giá của Tôi - Thành công")
    void getMyReviews_success() throws Exception {
      when(reviewService.getMyReviews(any(Authentication.class), any(Pageable.class)))
          .thenReturn(reviewResponsePage);

      mockMvc
          .perform(get("/api/reviews/my").param("page", "0").param("size", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].comment", is(reviewResponse.getComment())));
    }
  }
}
