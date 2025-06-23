package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.NotificationType;
import com.yourcompany.agritrade.common.model.ReviewStatus;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.interaction.domain.Review;
import com.yourcompany.agritrade.interaction.repository.ReviewRepository;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.repository.SupplyOrderRequestRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.response.*;
import com.yourcompany.agritrade.usermanagement.mapper.FarmerSummaryMapper;
import com.yourcompany.agritrade.usermanagement.mapper.UserMapper;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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
  @Mock private SupplyOrderRequestRepository supplyOrderRequestRepository;
  @Mock private OrderMapper orderMapper;
  @Mock private FileStorageService fileStorageService;
  @Mock private UserMapper userMapper;
  @Mock private FarmerSummaryMapper farmerSummaryMapper;
  @Mock private Authentication authentication;

  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private DashboardServiceImpl dashboardService;

  private User testFarmer;
  private User testBuyer;
  private final Long FARMER_ID = 1L;
  private final List<OrderStatus> REVENUE_STATUSES =
      Arrays.asList(OrderStatus.SHIPPING, OrderStatus.DELIVERED);
  private final List<OrderStatus> PENDING_STATUSES =
      Arrays.asList(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

  @BeforeEach
  void setUp() {
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    testFarmer = new User();
    testFarmer.setId(FARMER_ID);
    testFarmer.setFullName("Test Farmer");

    testBuyer = new User();
    testBuyer.setId(2L);
    testBuyer.setFullName("Test Buyer");
  }

  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  @Nested
  @DisplayName("Farmer Dashboard Tests")
  class FarmerDashboard {

    @BeforeEach
    void farmerSetup() {
      mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(testFarmer);
    }

    @Test
    @DisplayName("Get Farmer Dashboard Stats - Success")
    void getFarmerDashboardStats_success() {
      LocalDateTime todayStart = LocalDate.now().atStartOfDay();
      LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
      LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

      when(orderRepository.countByFarmerIdAndCreatedAtBetween(FARMER_ID, todayStart, todayEnd))
          .thenReturn(5L);
      when(orderRepository.countByFarmerIdAndCreatedAtBetween(FARMER_ID, monthStart, todayEnd))
          .thenReturn(20L);
      when(orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
              FARMER_ID, REVENUE_STATUSES, todayStart, todayEnd))
          .thenReturn(new BigDecimal("1500.00"));
      when(orderRepository.sumTotalAmountByFarmerIdAndStatusInAndCreatedAtBetween(
              FARMER_ID, REVENUE_STATUSES, monthStart, todayEnd))
          .thenReturn(new BigDecimal("10000.00"));
      when(orderRepository.countByFarmerIdAndStatusIn(FARMER_ID, PENDING_STATUSES)).thenReturn(3L);
      when(productRepository.countByFarmerIdAndB2bEnabledAndStockQuantityLessThan(
              FARMER_ID, false, 5))
          .thenReturn(2L);
      when(productRepository.countByFarmerIdAndB2bEnabledAndStockQuantityLessThan(
              FARMER_ID, true, 5))
          .thenReturn(1L);
      when(reviewRepository.countByProductFarmerIdAndStatus(FARMER_ID, ReviewStatus.PENDING))
          .thenReturn(4L);
      when(supplyOrderRequestRepository.countByFarmerIdAndStatus(
              FARMER_ID, SupplyOrderRequestStatus.PENDING_FARMER_ACTION))
          .thenReturn(6L);

      DashboardStatsResponse stats = dashboardService.getFarmerDashboardStats(authentication);

      assertNotNull(stats);
      assertEquals(5L, stats.getTotalOrdersToday());
      assertEquals(20L, stats.getTotalOrdersThisMonth());
      assertEquals(new BigDecimal("1500.00"), stats.getTotalRevenueToday());
      assertEquals(new BigDecimal("10000.00"), stats.getTotalRevenueThisMonth());
      assertEquals(3L, stats.getPendingOrders());
      assertEquals(2L, stats.getLowStockProducts());
      assertEquals(1L, stats.getLowStockSupplies());
      assertEquals(4L, stats.getPendingReviewsOnMyProducts());
      assertEquals(6L, stats.getPendingSupplyRequests());
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
      when(orderMapper.toOrderSummaryResponseList(orderPage.getContent()))
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
      List<TopProductResponse> topProductsFromRepo = List.of(topProduct1);

      Product p1 = new Product();
      p1.setId(201L);
      ProductImage img1 = new ProductImage();
      img1.setBlobPath("path/to/image1.jpg");
      img1.setDefault(true);
      p1.setImages(new HashSet<>(Set.of(img1)));

      when(productRepository.findTopSellingProductsByFarmerWithoutThumbnail(FARMER_ID, pageable))
          .thenReturn(topProductsFromRepo);
      when(productRepository.findByIdInWithImages(List.of(201L))).thenReturn(List.of(p1));
      when(fileStorageService.getFileUrl("path/to/image1.jpg")).thenReturn("signed-url-for-image1");

      List<TopProductResponse> result =
          dashboardService.getTopSellingFarmerProducts(authentication, limit);

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("signed-url-for-image1", result.get(0).getThumbnailUrl());
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
      LocalDateTime prevMonthStart = monthStart.minusMonths(1);
      LocalDateTime prevMonthEnd = monthStart.minusNanos(1);

      when(orderRepository.countByCreatedAtBetween(todayStart, todayEnd)).thenReturn(10L);
      when(orderRepository.countByCreatedAtBetween(monthStart, todayEnd)).thenReturn(50L);
      when(orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
              REVENUE_STATUSES, todayStart, todayEnd))
          .thenReturn(new BigDecimal("5000.00"));
      when(orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
              REVENUE_STATUSES, monthStart, todayEnd))
          .thenReturn(new BigDecimal("25000.00"));
      when(orderRepository.sumTotalAmountByStatusInAndCreatedAtBetween(
              REVENUE_STATUSES, prevMonthStart, prevMonthEnd))
          .thenReturn(new BigDecimal("20000.00"));

      when(userRepository.count()).thenReturn(100L);
      when(userRepository.countByRoleName(RoleType.ROLE_FARMER)).thenReturn(20L);
      when(userRepository.countByRoleName(RoleType.ROLE_CONSUMER)).thenReturn(70L);
      when(userRepository.countByRoleName(RoleType.ROLE_BUSINESS_BUYER)).thenReturn(10L);

      when(farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING))
          .thenReturn(5L);
      when(productRepository.countByStatus(ProductStatus.PENDING_APPROVAL)).thenReturn(8L);
      when(reviewRepository.countByStatus(ReviewStatus.PENDING)).thenReturn(12L);

      Arrays.stream(OrderStatus.values())
          .forEach(
              status ->
                  when(orderRepository.countByStatus(status))
                      .thenReturn(2L) // Giả sử mỗi status có 2 order
              );

      DashboardStatsResponse stats = dashboardService.getAdminDashboardStats();

      assertNotNull(stats);
      assertEquals(10L, stats.getTotalOrdersToday());
      assertEquals(50L, stats.getTotalOrdersThisMonth());
      assertEquals(new BigDecimal("5000.00"), stats.getTotalRevenueToday());
      assertEquals(new BigDecimal("25000.00"), stats.getTotalRevenueThisMonth());
      assertEquals(new BigDecimal("20000.00"), stats.getTotalRevenuePreviousMonth());
      assertEquals(100L, stats.getTotalUsers());
      assertEquals(5L, stats.getPendingFarmerApprovals());
      assertEquals(8L, stats.getPendingProductApprovals());
      assertEquals(12L, stats.getPendingReviews());
      assertNotNull(stats.getOrderStatusDistribution());
      assertEquals(2L, stats.getOrderStatusDistribution().get(OrderStatus.PENDING.name()));
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
      int limit = 5;
      Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

      Order recentOrder = new Order();
      recentOrder.setId(1L);
      recentOrder.setOrderCode("ORD-RECENT");
      recentOrder.setCreatedAt(LocalDateTime.now().minusHours(1));
      recentOrder.setBuyer(testBuyer);

      User recentUser = new User();
      recentUser.setId(10L);
      recentUser.setFullName("New User");
      recentUser.setEmail("new@example.com");
      recentUser.setCreatedAt(LocalDateTime.now().minusMinutes(30));

      Review recentReview = new Review();
      recentReview.setId(5L);
      Product reviewedProduct = new Product();
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
      assertEquals(3, result.size());
      // Sắp xếp theo thời gian, review là mới nhất
      assertEquals(NotificationType.REVIEW_PENDING, result.get(0).getType());
      assertEquals(NotificationType.WELCOME, result.get(1).getType());
      assertEquals(NotificationType.ORDER_PLACED, result.get(2).getType());
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
  @DisplayName("Get Farmer Stats - User Not Found - Throws UsernameNotFoundException")
  void getFarmerStats_whenUserNotFound_shouldThrowException() {
    mockedSecurityUtils
        .when(SecurityUtils::getCurrentAuthenticatedUser)
        .thenThrow(new UsernameNotFoundException("User not found"));

    assertThrows(
        UsernameNotFoundException.class,
        () -> dashboardService.getFarmerDashboardStats(authentication));
  }

  @Test
  @DisplayName("Get Farmer Stats - Not Authenticated - Throws AccessDeniedException")
  void getFarmerStats_whenNotAuthenticated_shouldThrowException() {
    mockedSecurityUtils
        .when(SecurityUtils::getCurrentAuthenticatedUser)
        .thenThrow(new AccessDeniedException("Not authenticated"));

    assertThrows(
        AccessDeniedException.class,
        () -> dashboardService.getFarmerDashboardStats(authentication));
  }
}
