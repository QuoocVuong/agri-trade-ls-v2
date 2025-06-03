package com.yourcompany.agritrade.interaction.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.ReviewStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ReviewMapper reviewMapper;
    @Mock private NotificationService notificationService;
    @Mock private Authentication authentication;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User testConsumer, testFarmer;
    private Product testProduct;
    private Order testOrder;
    private Review testReview;
    private ReviewRequest reviewRequest;
    private ReviewResponse reviewResponseDto;

    @BeforeEach
    void setUp() {
        testConsumer = new User();
        testConsumer.setId(1L);
        testConsumer.setEmail("consumer@example.com");
        testConsumer.setFullName("Test Consumer");

        testFarmer = new User(); // Dùng cho getReviewsForFarmerProducts
        testFarmer.setId(2L);
        testFarmer.setEmail("farmer@example.com");

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setName("Test Product");
        testProduct.setFarmer(testFarmer); // Gán farmer cho product
        testProduct.setAverageRating(0.0f);
        testProduct.setRatingCount(0);


        testOrder = new Order();
        testOrder.setId(100L);
        testOrder.setBuyer(testConsumer); // Consumer là người mua của đơn hàng này

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
        testReview.setStatus(ReviewStatus.PENDING); // Trạng thái ban đầu khi admin duyệt

        reviewResponseDto = new ReviewResponse();
        reviewResponseDto.setId(1L);
        reviewResponseDto.setRating(5);
        // ... các trường khác của DTO

        lenient().when(authentication.getName()).thenReturn(testConsumer.getEmail());
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(userRepository.findByEmail(testConsumer.getEmail())).thenReturn(Optional.of(testConsumer));
        lenient().when(userRepository.findByEmail(testFarmer.getEmail())).thenReturn(Optional.of(testFarmer));
    }

    private void mockAuthenticatedUser(User user) {
        when(authentication.getName()).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("Create Review Tests")
    class CreateReviewTests {
        @Test
        @DisplayName("Create Review - Success")
        void createReview_success() {
            mockAuthenticatedUser(testConsumer);
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(orderRepository.existsByIdAndBuyerIdAndOrderItemsProductId(testOrder.getId(), testConsumer.getId(), testProduct.getId())).thenReturn(true); // User đã mua
            when(reviewRepository.existsByConsumerIdAndProductIdAndOrderId(testConsumer.getId(), testProduct.getId(), testOrder.getId())).thenReturn(false); // Chưa review cho order này
            when(reviewRepository.existsByConsumerIdAndProductId(testConsumer.getId(), testProduct.getId())).thenReturn(false); // Chưa review sản phẩm này bao giờ
            when(orderRepository.findById(testOrder.getId())).thenReturn(Optional.of(testOrder));
            when(reviewMapper.requestToReview(reviewRequest)).thenReturn(testReview); // Giả sử mapper trả về testReview (chưa có ID)
            when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
                Review r = invocation.getArgument(0);
                r.setId(1L); // Gán ID sau khi save
                return r;
            });
            // Mock cho updateProductAverageRating
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct)); // Cần cho findById trong updateProductAverageRating
            when(reviewRepository.getAverageRatingAndCountByProductIdAndStatus(testProduct.getId(), ReviewStatus.APPROVED))
                    .thenReturn(Optional.of(new Object[]{5.0, 1L})); // Giả sử rating mới là 5, count là 1
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            when(reviewMapper.toReviewResponse(any(Review.class))).thenReturn(reviewResponseDto);
            doNothing().when(notificationService).sendReviewApprovedNotification(any(Review.class));

            ReviewResponse result = reviewService.createReview(authentication, reviewRequest);

            assertNotNull(result);
            assertEquals(reviewResponseDto.getId(), result.getId());
            assertEquals(ReviewStatus.APPROVED, testReview.getStatus()); // Kiểm tra status được set đúng
            assertEquals(5.0f, testProduct.getAverageRating()); // Kiểm tra rating sản phẩm được cập nhật
            assertEquals(1, testProduct.getRatingCount());

            verify(reviewRepository).save(any(Review.class));
            verify(productRepository, times(1)).save(any(Product.class)); // 1 lần cho update rating
            verify(notificationService).sendReviewApprovedNotification(any(Review.class));
        }

        @Test
        @DisplayName("Create Review - User Has Not Purchased Product - Throws BadRequestException")
        void createReview_userHasNotPurchased_throwsBadRequest() {
            mockAuthenticatedUser(testConsumer);
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(orderRepository.existsByIdAndBuyerIdAndOrderItemsProductId(testOrder.getId(), testConsumer.getId(), testProduct.getId())).thenReturn(false); // User chưa mua

            assertThrows(BadRequestException.class,
                    () -> reviewService.createReview(authentication, reviewRequest));
        }

        @Test
        @DisplayName("Create Review - Already Reviewed For This Order - Throws BadRequestException")
        void createReview_alreadyReviewedForOrder_throwsBadRequest() {
            mockAuthenticatedUser(testConsumer);
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(orderRepository.existsByIdAndBuyerIdAndOrderItemsProductId(testOrder.getId(), testConsumer.getId(), testProduct.getId())).thenReturn(true);
            when(reviewRepository.existsByConsumerIdAndProductIdAndOrderId(testConsumer.getId(), testProduct.getId(), testOrder.getId())).thenReturn(true); // Đã review

            assertThrows(BadRequestException.class,
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
            Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);
            testReview.setStatus(ReviewStatus.APPROVED); // Đảm bảo review đã approved

            when(productRepository.existsById(testProduct.getId())).thenReturn(true);
            when(reviewRepository.findByProductIdAndStatus(testProduct.getId(), ReviewStatus.APPROVED, pageable))
                    .thenReturn(reviewPage);
            when(reviewMapper.toReviewResponsePage(reviewPage)).thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

            Page<ReviewResponse> result = reviewService.getApprovedReviewsByProduct(testProduct.getId(), pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Get My Reviews - Success")
        void getMyReviews_success() {
            mockAuthenticatedUser(testConsumer);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);

            when(reviewRepository.findByConsumerId(testConsumer.getId(), pageable)).thenReturn(reviewPage);
            when(reviewMapper.toReviewResponsePage(reviewPage)).thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

            Page<ReviewResponse> result = reviewService.getMyReviews(authentication, pageable);
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Get Reviews For Farmer Products - Success")
        void getReviewsForFarmerProducts_success() {
            mockAuthenticatedUser(testFarmer); // Farmer đang đăng nhập
            Pageable pageable = PageRequest.of(0, 10);
            Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1); // testReview có product của testFarmer

            when(reviewRepository.findReviewsForFarmerProducts(testFarmer.getId(), pageable)).thenReturn(reviewPage);
            when(reviewMapper.toReviewResponsePage(reviewPage)).thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

            Page<ReviewResponse> result = reviewService.getReviewsForFarmerProducts(authentication, pageable);
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
            Page<Review> reviewPage = new PageImpl<>(List.of(testReview), pageable, 1);
            testReview.setStatus(ReviewStatus.PENDING);

            when(reviewRepository.findByStatus(ReviewStatus.PENDING, pageable)).thenReturn(reviewPage);
            when(reviewMapper.toReviewResponsePage(reviewPage)).thenReturn(new PageImpl<>(List.of(reviewResponseDto)));

            Page<ReviewResponse> result = reviewService.getReviewsByStatus(ReviewStatus.PENDING, pageable);
            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Approve Review - Success")
        void approveReview_success() {
            testReview.setStatus(ReviewStatus.PENDING);
            when(reviewRepository.findById(testReview.getId())).thenReturn(Optional.of(testReview));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
            // Mock cho updateProductAverageRating
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(reviewRepository.getAverageRatingAndCountByProductIdAndStatus(testProduct.getId(), ReviewStatus.APPROVED))
                    .thenReturn(Optional.of(new Object[]{4.0, 2L})); // Giả sử sau khi duyệt, rating là 4, count là 2
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            when(reviewMapper.toReviewResponse(any(Review.class))).thenReturn(reviewResponseDto);
            doNothing().when(notificationService).sendReviewApprovedNotification(any(Review.class));

            ReviewResponse result = reviewService.approveReview(testReview.getId());

            assertNotNull(result);
            assertEquals(ReviewStatus.APPROVED, testReview.getStatus());
            assertEquals(4.0f, testProduct.getAverageRating());
            assertEquals(2, testProduct.getRatingCount());
            verify(notificationService).sendReviewApprovedNotification(testReview);
        }

        @Test
        @DisplayName("Reject Review - From Approved - Success")
        void rejectReview_fromApproved_success() {
            testReview.setStatus(ReviewStatus.APPROVED); // Review đã được duyệt trước đó
            testProduct.setAverageRating(5.0f); // Giả sử rating hiện tại
            testProduct.setRatingCount(1);

            when(reviewRepository.findById(testReview.getId())).thenReturn(Optional.of(testReview));
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
            // Mock cho updateProductAverageRating (giả sử sau khi từ chối, không còn review nào)
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(reviewRepository.getAverageRatingAndCountByProductIdAndStatus(testProduct.getId(), ReviewStatus.APPROVED))
                    .thenReturn(Optional.empty()); // Không còn review approved nào
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            when(reviewMapper.toReviewResponse(any(Review.class))).thenReturn(reviewResponseDto);
            doNothing().when(notificationService).sendReviewRejectedNotification(any(Review.class));

            ReviewResponse result = reviewService.rejectReview(testReview.getId());

            assertNotNull(result);
            assertEquals(ReviewStatus.REJECTED, testReview.getStatus());
            assertEquals(0.0f, testProduct.getAverageRating()); // Rating được reset
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
            // Mock cho updateProductAverageRating
            when(productRepository.findById(testProduct.getId())).thenReturn(Optional.of(testProduct));
            when(reviewRepository.getAverageRatingAndCountByProductIdAndStatus(testProduct.getId(), ReviewStatus.APPROVED))
                    .thenReturn(Optional.empty()); // Sau khi xóa, không còn review approved
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            reviewService.deleteReview(testReview.getId());

            verify(reviewRepository).delete(testReview);
            assertEquals(0.0f, testProduct.getAverageRating());
            assertEquals(0, testProduct.getRatingCount());
        }
    }

    @Test
    @DisplayName("Get User From Authentication - User Not Found - Throws UsernameNotFoundException")
    void getUserFromAuthentication_whenUserNotFound_shouldThrowUsernameNotFoundException() {
        when(authentication.getName()).thenReturn("unknown@example.com");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> reviewService.createReview(authentication, reviewRequest));
    }
}