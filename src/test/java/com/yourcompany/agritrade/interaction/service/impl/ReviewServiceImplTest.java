package com.yourcompany.agritrade.interaction.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.interaction.dto.request.ReviewRequest;
import com.yourcompany.agritrade.interaction.dto.response.ReviewResponse;
import com.yourcompany.agritrade.interaction.mapper.ReviewMapper;
import com.yourcompany.agritrade.interaction.repository.ReviewRepository;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

  @Mock private ReviewRepository reviewRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;
  @Mock private OrderRepository orderRepository;
  @Mock private ReviewMapper reviewMapper;
  @Mock private NotificationService notificationService;
  @Mock private Authentication authentication;

  // SỬA LỖI: Thêm MockedStatic để quản lý mock cho lớp tiện ích SecurityUtils
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private ReviewServiceImpl reviewService;

  private User testConsumer, testFarmer;
  private Product testProduct;
  private Order testOrder;
  private Review testReview;
  private ReviewRequest reviewRequest;
  private ReviewResponse reviewResponseDto;

  @BeforeEach
  void setUp() {
    // SỬA LỖI: Khởi tạo mock static cho SecurityUtils trước mỗi test
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    testConsumer = new User();
    testConsumer.setId(1L);
    testConsumer.setEmail("consumer@example.com");
    testConsumer.setFullName("Test Consumer");

    testFarmer = new User();
    testFarmer.setId(2L);
    testFarmer.setEmail("farmer@example.com");

    testProduct = new Product();
    testProduct.setId(10L);
    testProduct.setName("Test Product");
    testProduct.setFarmer(testFarmer);
    testProduct.setAverageRating(0.0f);
    testProduct.setRatingCount(0);

    testOrder = new Order();
    testOrder.setId(100L);
    testOrder.setBuyer(testConsumer);

    reviewRequest = new ReviewRequest();
    reviewRequest.setProductId(testProduct.getId());
    reviewRequest.setOrderId(testOrder.getId());
    reviewRequest.setRating(5);
    reviewRequest.setComment("Great product!");

    testReview = new Review();
    testReview.setId(1L);
    testReview.setConsumer(testConsumer);
    testReview.setProduct(testProduct);
    testReview.setOrder(testOrder);
    testReview.setRating(5);
    testReview.setComment("Great product!");
    testReview.setStatus(ReviewStatus.PENDING);

    reviewResponseDto = new ReviewResponse();
    reviewResponseDto.setId(1L);
    reviewResponseDto.setRating(5);
  }

  // SỬA LỖI: Thêm tearDown để đóng mock static sau mỗi test
  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  private void mockAuthenticatedUser(User user) {
    // SỬA LỖI: Mock SecurityUtils thay vì các mock không được dùng đến
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(user);
  }

  @Nested
  @DisplayName("Create Review Tests")
  class CreateReviewTests {

    @Test
    @DisplayName("Create Review - User Has Not Purchased Product - Throws BadRequestException")
    void createReview_userHasNotPurchased_throwsBadRequest() {
      mockAuthenticatedUser(testConsumer);
      when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
      when(orderRepository.existsByIdAndBuyerIdAndOrderItemsProductId(
              testOrder.getId(), testConsumer.getId(), testProduct.getId()))
          .thenReturn(false);

      assertThrows(
          BadRequestException.class,
          () -> reviewService.createReview(authentication, reviewRequest));
    }

    @Test
    @DisplayName("Create Review - Already Reviewed For This Order - Throws BadRequestException")
    void createReview_alreadyReviewedForOrder_throwsBadRequest() {
      mockAuthenticatedUser(testConsumer);
      when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
      when(orderRepository.existsByIdAndBuyerIdAndOrderItemsProductId(
              testOrder.getId(), testConsumer.getId(), testProduct.getId()))
          .thenReturn(true);
      when(reviewRepository.existsByConsumerIdAndProductIdAndOrderId(
              testConsumer.getId(), testProduct.getId(), testOrder.getId()))
          .thenReturn(true);

      assertThrows(
          BadRequestException.class,
          () -> reviewService.createReview(authentication, reviewRequest));
    }
  }

  @Nested
  @DisplayName("Get Review Tests")
  class GetReviewTests {
    @Test
    @DisplayName("Get Approved Reviews By Product - Success")
    void getApprovedReviewsByProduct_success() {
      Pageable pageable = PageRequest.of(0, 10);
      testReview.setStatus(ReviewStatus.APPROVED);
      Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);

      when(productRepository.existsById(testProduct.getId())).thenReturn(true);
      when(reviewRepository.findByProductIdAndStatus(
              testProduct.getId(), ReviewStatus.APPROVED, pageable))
          .thenReturn(reviewPage);
      when(reviewMapper.toReviewResponsePage(reviewPage))
          .thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

      Page<ReviewResponse> result =
          reviewService.getApprovedReviewsByProduct(testProduct.getId(), pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Get My Reviews - Success")
    void getMyReviews_success() {
      mockAuthenticatedUser(testConsumer);
      Pageable pageable = PageRequest.of(0, 10);
      Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);

      when(reviewRepository.findByConsumerId(testConsumer.getId(), pageable))
          .thenReturn(reviewPage);
      when(reviewMapper.toReviewResponsePage(reviewPage))
          .thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

      Page<ReviewResponse> result = reviewService.getMyReviews(authentication, pageable);
      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Get Reviews For Farmer Products - Success")
    void getReviewsForFarmerProducts_success() {
      mockAuthenticatedUser(testFarmer);
      Pageable pageable = PageRequest.of(0, 10);
      Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);

      when(reviewRepository.findReviewsForFarmerProducts(testFarmer.getId(), pageable))
          .thenReturn(reviewPage);
      when(reviewMapper.toReviewResponsePage(reviewPage))
          .thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

      Page<ReviewResponse> result =
          reviewService.getReviewsForFarmerProducts(authentication, pageable);
      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }
  }

  @Nested
  @DisplayName("Admin Review Management Tests")
  class AdminReviewManagementTests {
    @Test
    @DisplayName("Get Reviews By Status - Success")
    void getReviewsByStatus_success() {
      Pageable pageable = PageRequest.of(0, 10);
      testReview.setStatus(ReviewStatus.PENDING);
      Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);

      when(reviewRepository.findByStatus(ReviewStatus.PENDING, pageable)).thenReturn(reviewPage);
      when(reviewMapper.toReviewResponsePage(reviewPage))
          .thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

      Page<ReviewResponse> result =
          reviewService.getReviewsByStatus(ReviewStatus.PENDING, pageable);
      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Reject Review - From Approved - Success")
    void rejectReview_fromApproved_success() {
      testReview.setStatus(ReviewStatus.APPROVED);
      testProduct.setAverageRating(5.0f);
      testProduct.setRatingCount(1);

      when(reviewRepository.findById(testReview.getId())).thenReturn(Optional.of(testReview));
      when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
      when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
      when(reviewRepository.getAverageRatingAndCountByProductIdAndStatus(
              testProduct.getId(), ReviewStatus.APPROVED))
          .thenReturn(Optional.empty());
      when(productRepository.save(any(Product.class))).thenReturn(testProduct);
      when(reviewMapper.toReviewResponse(any(Review.class))).thenReturn(reviewResponseDto);
      doNothing().when(notificationService).sendReviewRejectedNotification(any(Review.class));

      ReviewResponse result = reviewService.rejectReview(testReview.getId());

      assertNotNull(result);
      assertEquals(ReviewStatus.REJECTED, testReview.getStatus());
      assertEquals(0.0f, testProduct.getAverageRating());
      assertEquals(0, testProduct.getRatingCount());
      verify(notificationService).sendReviewRejectedNotification(testReview);
    }

    @Test
    @DisplayName("Delete Review - Approved Review - Success")
    void deleteReview_approvedReview_success() {
      testReview.setStatus(ReviewStatus.APPROVED);
      testProduct.setAverageRating(5.0f);
      testProduct.setRatingCount(1);

      when(reviewRepository.findById(testReview.getId())).thenReturn(Optional.of(testReview));
      doNothing().when(reviewRepository).delete(testReview);
      when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
      when(reviewRepository.getAverageRatingAndCountByProductIdAndStatus(
              testProduct.getId(), ReviewStatus.APPROVED))
          .thenReturn(Optional.empty());
      when(productRepository.save(any(Product.class))).thenReturn(testProduct);

      reviewService.deleteReview(testReview.getId());

      verify(reviewRepository).delete(testReview);
      assertEquals(0.0f, testProduct.getAverageRating());
      assertEquals(0, testProduct.getRatingCount());
    }
  }
}
