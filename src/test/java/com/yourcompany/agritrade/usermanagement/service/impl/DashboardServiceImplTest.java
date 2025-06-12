package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.interaction.repository.ReviewRepository;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerChartDataResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.RecentActivityResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.TimeSeriesDataPoint;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

  @Mock private OrderRepository orderRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;
  @Mock private FarmerProfileRepository farmerProfileRepository;
  @Mock private ReviewRepository reviewRepository;
  @Mock private OrderMapper orderMapper;
  @Mock private Authentication authentication;

  @InjectMocks private DashboardServiceImpl dashboardService;

  private User testFarmer;
  private final Long FARMER_ID = 1L;
  private final String FARMER_EMAIL = "farmer@example.com";
  private final List<OrderStatus> REVENUE_STATUSES =
      Arrays.asList(OrderStatus.SHIPPING, OrderStatus.DELIVERED);
  private final List<OrderStatus> PENDING_STATUSES =
      Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

  @BeforeEach
  void setUp() {
    testFarmer = new User();
    testFarmer.setId(FARMER_ID);
    testFarmer.setEmail(FARMER_EMAIL);
    testFarmer.setFullName("Test Farmer");

    lenient().when(authentication.getName()).thenReturn(FARMER_EMAIL);
    lenient().when(authentication.isAuthenticated()).thenReturn(true);
    lenient().when(userRepository.findByEmail(FARMER_EMAIL)).thenReturn(Optional.of(testFarmer));
  }

  @Nested
  @DisplayName("Farmer Dashboard Tests")
  class FarmerDashboard {
    @Test
    @DisplayName("Get Farmer Dashboard Stats - Success")
    void getFarmerDashboardStats_success() {
      LocalDateTime todayStart = LocalDate.now().atStartOfDay();
      LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
      LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

      when(orderRepository.countByFarmerIdAndCreatedAtBetween(
              eq(FARMER_ID), eq(todayStart), eq(todayEnd)))
          .thenReturn(5L);
      when(orderRepository.countByFarmerIdAndCreatedAtBetween(
              eq(FARMER_ID), eq(monthStart), eq(todayEnd)))
          .thenReturn(20L);
      when(orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
              eq(FARMER_ID), eq(REVENUE_STATUSES), eq(todayStart), eq(todayEnd)))
          .thenReturn(new BigDecimal("1500.00"));
      when(orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
              eq(FARMER_ID), eq(REVENUE_STATUSES), eq(monthStart), eq(todayEnd)))
          .thenReturn(new BigDecimal("10000.00"));
      when(orderRepository.countByFarmerIdAndStatusIn(eq(FARMER_ID), eq(PENDING_STATUSES)))
          .thenReturn(3L);
      when(productRepository.countByFarmerIdAndStockQuantityLessThan(eq(FARMER_ID), anyInt()))
          .thenReturn(2L);
      when(reviewRepository.countByProductFarmerIdAndStatus(
              eq(FARMER_ID), eq(ReviewStatus.PENDING)))
          .thenReturn(1L);

      DashboardStatsResponse stats = dashboardService.getFarmerDashboardStats(authentication);

      assertNotNull(stats);
      assertEquals(5L, stats.getTotalOrdersToday());
      assertEquals(20L, stats.getTotalOrdersThisMonth());
      assertEquals(new BigDecimal("1500.00"), stats.getTotalRevenueToday());
      assertEquals(new BigDecimal("10000.00"), stats.getTotalRevenueThisMonth());
      assertEquals(3L, stats.getPendingOrders());
      assertEquals(2L, stats.getLowStockProducts());
      assertEquals(1L, stats.getPendingReviewsOnMyProducts());
    }

    @Test
    @DisplayName("Get Recent Farmer Orders - Success")
    void getRecentFarmerOrders_success() {
      int limit = 3;
      Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
      Order order1 = new Order();
      order1.setId(101L);
      Order order2 = new Order();
      order2.setId(102L);
      Page<Order> orderPage = new PageImpl<>(List.of(order1, order2), pageable, 2);
      OrderSummaryResponse summary1 = new OrderSummaryResponse();
      summary1.setId(101L);
      OrderSummaryResponse summary2 = new OrderSummaryResponse();
      summary2.setId(102L);

      when(orderRepository.findByFarmerIdWithDetails(FARMER_ID, pageable)).thenReturn(orderPage);
      when(orderMapper.toOrderSummaryResponseList(List.of(order1, order2)))
          .thenReturn(List.of(summary1, summary2));

      List<OrderSummaryResponse> result =
          dashboardService.getRecentFarmerOrders(authentication, limit);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertEquals(101L, result.get(0).getId());
    }

    @Test
    @DisplayName("Get Top Selling Farmer Products - Success")
    void getTopSellingFarmerProducts_success() {
      int limit = 2;
      Pageable pageable = PageRequest.of(0, limit);
      TopProductResponse topProduct1 =
          new TopProductResponse(201L, "Product X", "prod-x", 10L, new BigDecimal("1000"));
      TopProductResponse topProduct2 =
          new TopProductResponse(202L, "Product Y", "prod-y", 8L, new BigDecimal("800"));
      List<TopProductResponse> topProductsFromRepo = List.of(topProduct1, topProduct2);

      when(productRepository.findTopSellingProductsByFarmerWithoutThumbnail(FARMER_ID, pageable))
          .thenReturn(topProductsFromRepo);
      // Giả sử productRepository.findById trả về product có ảnh để test logic gán thumbnail
      com.yourcompany.agritrade.catalog.domain.Product p1 =
          new com.yourcompany.agritrade.catalog.domain.Product();
      p1.setId(201L);
      ProductImage img1 = new ProductImage();
      img1.setImageUrl("url1");
      img1.setDefault(true);
      p1.setImages(new HashSet<>(Set.of(img1)));
      when(productRepository.findById(201L)).thenReturn(Optional.of(p1));

      List<TopProductResponse> result =
          dashboardService.getTopSellingFarmerProducts(authentication, limit);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertEquals("url1", result.get(0).getThumbnailUrl()); // Kiểm tra thumbnail đã được gán
    }

    @Test
    @DisplayName("Get Farmer Order Count Chart Data - Success")
    void getFarmerOrderCountChartData_success() {
      LocalDate startDate = LocalDate.now().minusDays(2);
      LocalDate endDate = LocalDate.now();
      LocalDateTime startDateTime = startDate.atStartOfDay();
      LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

      Tuple tuple1 = mock(Tuple.class);
      when(tuple1.get(0)).thenReturn(Date.valueOf(startDate)); // Trả về java.sql.Date
      when(tuple1.get(1, Long.class)).thenReturn(5L);

      Tuple tuple2 = mock(Tuple.class);
      when(tuple2.get(0)).thenReturn(Date.valueOf(endDate));
      when(tuple2.get(1, Long.class)).thenReturn(10L);

      when(orderRepository.countOrdersByDayForFarmer(FARMER_ID, startDateTime, endDateTime))
          .thenReturn(List.of(tuple1, tuple2));

      List<FarmerChartDataResponse> result =
          dashboardService.getFarmerOrderCountChartData(authentication, startDate, endDate);

      assertNotNull(result);
      assertEquals(3, result.size()); // startDate, startDate+1, endDate
      assertEquals(5L, result.get(0).getValue().longValue());
      assertEquals(0L, result.get(1).getValue().longValue()); // Ngày ở giữa không có data
      assertEquals(10L, result.get(2).getValue().longValue());
      assertEquals(startDate.toString(), result.get(0).getLabel());
    }

    @Test
    @DisplayName("Get Farmer Revenue Chart Data - Success")
    void getFarmerRevenueChartData_success() {
      LocalDate startDate = LocalDate.now().minusDays(1);
      LocalDate endDate = LocalDate.now();
      LocalDateTime startDateTime = startDate.atStartOfDay();
      LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

      Tuple tuple1 = mock(Tuple.class);
      when(tuple1.get(0)).thenReturn(Date.valueOf(startDate));
      when(tuple1.get(1, BigDecimal.class)).thenReturn(new BigDecimal("120.50"));

      when(orderRepository.sumRevenueByDayForFarmer(
              FARMER_ID, startDateTime, endDateTime, REVENUE_STATUSES))
          .thenReturn(List.of(tuple1));

      List<FarmerChartDataResponse> result =
          dashboardService.getFarmerRevenueChartData(authentication, startDate, endDate);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertEquals(new BigDecimal("120.50"), result.get(0).getValue());
      assertEquals(BigDecimal.ZERO, result.get(1).getValue());
      assertEquals(startDate.toString(), result.get(0).getLabel());
    }
  }

  @Nested
  @DisplayName("Admin Dashboard Tests")
  class AdminDashboard {
    @Test
    @DisplayName("Get Admin Dashboard Stats - Success")
    void getAdminDashboardStats_success() {
      LocalDateTime todayStart = LocalDate.now().atStartOfDay();
      LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
      LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

      when(orderRepository.countByCreatedAtBetween(eq(todayStart), eq(todayEnd))).thenReturn(10L);
      when(orderRepository.countByCreatedAtBetween(eq(monthStart), eq(todayEnd))).thenReturn(50L);
      when(orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
              eq(REVENUE_STATUSES), eq(todayStart), eq(todayEnd)))
          .thenReturn(new BigDecimal("5000.00"));
      when(orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
              eq(REVENUE_STATUSES), eq(monthStart), eq(todayEnd)))
          .thenReturn(new BigDecimal("25000.00"));
      when(userRepository.count()).thenReturn(100L);
      when(userRepository.countByRoleName(RoleType.ROLE_FARMER)).thenReturn(20L);
      when(userRepository.countByRoleName(RoleType.ROLE_CONSUMER)).thenReturn(70L);
      when(userRepository.countByRoleName(RoleType.ROLE_BUSINESS_BUYER)).thenReturn(10L);
      when(farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING))
          .thenReturn(5L);
      when(productRepository.countByStatus(ProductStatus.PENDING_APPROVAL)).thenReturn(8L);
      when(reviewRepository.countByStatus(ReviewStatus.PENDING)).thenReturn(12L);

      DashboardStatsResponse stats = dashboardService.getAdminDashboardStats();

      assertNotNull(stats);
      assertEquals(10L, stats.getTotalOrdersToday());
      assertEquals(50L, stats.getTotalOrdersThisMonth());
      assertEquals(new BigDecimal("5000.00"), stats.getTotalRevenueToday());
      assertEquals(100L, stats.getTotalUsers());
      assertEquals(5L, stats.getPendingFarmerApprovals());
      assertEquals(8L, stats.getPendingProductApprovals());
      assertEquals(12L, stats.getPendingReviews());
    }

    @Test
    @DisplayName("Get Daily Revenue For Admin Chart - Success")
    void getDailyRevenueForAdminChart_success() {
      LocalDate startDate = LocalDate.now().minusDays(1);
      LocalDate endDate = LocalDate.now();
      LocalDateTime startDateTime = startDate.atStartOfDay();
      LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

      Object[] row1 = {Date.valueOf(startDate), new BigDecimal("1000.00")};
      Object[] row2 = {Date.valueOf(endDate), new BigDecimal("1500.00")};
      when(orderRepository.findDailyRevenueBetween(REVENUE_STATUSES, startDateTime, endDateTime))
          .thenReturn(List.of(row1, row2));

      List<TimeSeriesDataPoint<BigDecimal>> result =
          dashboardService.getDailyRevenueForAdminChart(startDate, endDate);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertEquals(startDate, result.get(0).getDate());
      assertEquals(new BigDecimal("1000.00"), result.get(0).getValue());
    }

    @Test
    @DisplayName("Get Daily Order Count For Admin Chart - Success")
    void getDailyOrderCountForAdminChart_success() {
      LocalDate startDate = LocalDate.now().minusDays(1);
      LocalDate endDate = LocalDate.now();
      LocalDateTime startDateTime = startDate.atStartOfDay();
      LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

      Object[] row1 = {Date.valueOf(startDate), 5L};
      Object[] row2 = {Date.valueOf(endDate), 8L};
      when(orderRepository.findDailyOrderCountBetween(startDateTime, endDateTime))
          .thenReturn(List.of(row1, row2));

      List<TimeSeriesDataPoint<Long>> result =
          dashboardService.getDailyOrderCountForAdminChart(startDate, endDate);

      assertNotNull(result);
      assertEquals(2, result.size());
      assertEquals(startDate, result.get(0).getDate());
      assertEquals(5L, result.get(0).getValue());
    }

    @Test
    @DisplayName("Get Recent Activities For Admin - Success")
    void getRecentActivitiesForAdmin_success() {
      int limit = 2;
      Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

      Order recentOrder = new Order();
      recentOrder.setId(1L);
      recentOrder.setOrderCode("ORD-RECENT");
      recentOrder.setCreatedAt(LocalDateTime.now().minusHours(1));
      User buyerForRecentOrder = new User();
      buyerForRecentOrder.setFullName("Recent Buyer");
      recentOrder.setBuyer(buyerForRecentOrder);

      User recentUser = new User();
      recentUser.setId(10L);
      recentUser.setFullName("New User");
      recentUser.setEmail("new@example.com");
      recentUser.setCreatedAt(LocalDateTime.now().minusMinutes(30));

      com.yourcompany.agritrade.interaction.domain.Review recentReview =
          new com.yourcompany.agritrade.interaction.domain.Review();
      recentReview.setId(5L);
      com.yourcompany.agritrade.catalog.domain.Product reviewedProduct =
          new com.yourcompany.agritrade.catalog.domain.Product();
      reviewedProduct.setId(50L);
      recentReview.setProduct(reviewedProduct);
      recentReview.setCreatedAt(LocalDateTime.now().minusMinutes(15));

      when(orderRepository.findTopNByOrderByCreatedAtDesc(pageable))
          .thenReturn(List.of(recentOrder));
      when(userRepository.findTopNByOrderByCreatedAtDesc(pageable)).thenReturn(List.of(recentUser));
      when(reviewRepository.findTopNByStatusOrderByCreatedAtDesc(ReviewStatus.PENDING, pageable))
          .thenReturn(List.of(recentReview));

      List<RecentActivityResponse> result = dashboardService.getRecentActivitiesForAdmin(limit);

      assertNotNull(result);
      // Kết quả sẽ được sắp xếp lại và giới hạn, nên số lượng có thể là `limit` hoặc ít hơn
      assertTrue(result.size() <= limit);
      // Kiểm tra xem các loại hoạt động khác nhau có được thêm vào không (tùy thuộc vào timestamp)
      boolean hasOrderActivity =
          result.stream().anyMatch(r -> r.getType() == NotificationType.ORDER_PLACED);
      boolean hasUserActivity =
          result.stream().anyMatch(r -> r.getType() == NotificationType.WELCOME);
      boolean hasReviewActivity =
          result.stream().anyMatch(r -> r.getType() == NotificationType.REVIEW_PENDING);

      // Ít nhất một trong số chúng phải có mặt nếu limit đủ lớn
      if (limit >= 1) assertTrue(hasOrderActivity || hasUserActivity || hasReviewActivity);
    }

    @Test
    @DisplayName("Get Pending Approval Counts - Success")
    void getPendingApprovalCounts_success() {
      when(farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING))
          .thenReturn(3L);
      when(productRepository.countByStatus(ProductStatus.PENDING_APPROVAL)).thenReturn(5L);
      when(reviewRepository.countByStatus(ReviewStatus.PENDING)).thenReturn(2L);

      Map<String, Long> counts = dashboardService.getPendingApprovalCounts();

      assertNotNull(counts);
      assertEquals(3L, counts.get("farmers"));
      assertEquals(5L, counts.get("products"));
      assertEquals(2L, counts.get("reviews"));
    }
  }

  @Test
  @DisplayName("Get User From Authentication - User Not Found - Throws UsernameNotFoundException")
  void getUserFromAuthentication_whenUserNotFound_shouldThrowUsernameNotFoundException() {
    String unknownEmail = "unknown@example.com";
    when(authentication.getName()).thenReturn(unknownEmail);
    when(authentication.isAuthenticated())
        .thenReturn(true); // <<< QUAN TRỌNG: Đảm bảo user được coi là đã xác thực
    when(userRepository.findByEmail(unknownEmail))
        .thenReturn(Optional.empty()); // User không tồn tại

    // Gọi một phương thức bất kỳ trong service mà sử dụng getUserFromAuthentication
    assertThrows(
        UsernameNotFoundException.class,
        () -> dashboardService.getFarmerDashboardStats(authentication));
  }

  @Test
  @DisplayName("Get User From Authentication - Not Authenticated - Throws AccessDeniedException")
  void getUserFromAuthentication_whenNotAuthenticated_shouldThrowAccessDeniedException() {
    when(authentication.isAuthenticated()).thenReturn(false); // Giả lập chưa xác thực
    // Không cần mock getName() hoặc userRepository.findByEmail() vì service sẽ ném lỗi trước đó

    assertThrows(
        AccessDeniedException.class,
        () -> dashboardService.getFarmerDashboardStats(authentication));
  }
}
