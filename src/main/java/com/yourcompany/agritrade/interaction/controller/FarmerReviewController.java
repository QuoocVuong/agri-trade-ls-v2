package com.yourcompany.agritrade.interaction.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farmer/reviews") // <<< Đường dẫn API mới cho farmer
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('FARMER')") // Yêu cầu quyền FARMER cho cả controller
public class FarmerReviewController {

  private final ReviewService reviewService;

  @GetMapping
  public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getMyProductReviews(
      Authentication authentication,
      // (Tùy chọn) Thêm RequestParam để lọc theo status
      // @RequestParam(required = false) ReviewStatus status,
      @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    log.info("API /api/farmer/reviews called by farmer: {}", authentication.getName());
    Page<ReviewResponse> reviews;
    // if (status != null) {
    //     reviews = reviewService.getReviewsForFarmerProductsByStatus(authentication, status,
    // pageable);
    // } else {
    reviews = reviewService.getReviewsForFarmerProducts(authentication, pageable);
    // }
    return ResponseEntity.ok(ApiResponse.success(reviews));
  }

  // Có thể thêm các endpoint khác cho farmer liên quan đến review ở đây (ví dụ: trả lời review)
}
