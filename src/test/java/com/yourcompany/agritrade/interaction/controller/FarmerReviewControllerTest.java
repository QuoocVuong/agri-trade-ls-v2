package com.yourcompany.agritrade.interaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FarmerReviewController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(roles = {"FARMER"}) // Tất cả API trong controller này yêu cầu vai trò FARMER
class FarmerReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    // Authentication sẽ được cung cấp bởi @WithMockUser

    private ReviewResponse reviewResponse;
    private Page<ReviewResponse> reviewResponsePage;

    @BeforeEach
    void setUp() {
        reviewResponse = new ReviewResponse();
        reviewResponse.setId(1L);
        reviewResponse.setComment("Đánh giá tốt từ khách hàng.");
        reviewResponse.setRating(5);
        reviewResponse.setCreatedAt(LocalDateTime.now());
        // ... các trường khác của ReviewResponse

        reviewResponsePage = new PageImpl<>(List.of(reviewResponse));
    }

    @Test
    @DisplayName("GET /api/farmer/reviews - Lấy Đánh giá Sản phẩm của Tôi (Farmer) - Thành công")
    void getMyProductReviews_success() throws Exception {
        when(reviewService.getReviewsForFarmerProducts(any(Authentication.class), any(Pageable.class)))
                .thenReturn(reviewResponsePage);

        mockMvc.perform(get("/api/farmer/reviews")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].comment", is(reviewResponse.getComment())));

        verify(reviewService).getReviewsForFarmerProducts(any(Authentication.class), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/farmer/reviews - Lấy Đánh giá Sản phẩm của Tôi (Farmer) - Không có đánh giá")
    void getMyProductReviews_emptyList() throws Exception {
        Page<ReviewResponse> emptyPage = new PageImpl<>(Collections.emptyList());
        when(reviewService.getReviewsForFarmerProducts(any(Authentication.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/farmer/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        verify(reviewService).getReviewsForFarmerProducts(any(Authentication.class), any(Pageable.class));
    }

    // Nếu bạn quyết định thêm tham số `status` vào controller, bạn có thể thêm test case cho nó:
    /*
    @Test
    @DisplayName("GET /api/farmer/reviews - Lấy Đánh giá Sản phẩm của Tôi (Farmer) - Với bộ lọc trạng thái")
    void getMyProductReviews_withStatusFilter_success() throws Exception {
        ReviewStatus statusFilter = ReviewStatus.APPROVED;
        when(reviewService.getReviewsForFarmerProductsByStatus(any(Authentication.class), eq(statusFilter), any(Pageable.class)))
                .thenReturn(reviewResponsePage);

        mockMvc.perform(get("/api/farmer/reviews")
                        .param("status", statusFilter.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        verify(reviewService).getReviewsForFarmerProductsByStatus(any(Authentication.class), eq(statusFilter), any(Pageable.class));
    }
    */
}
