package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

public interface DashboardService {

  /** Lấy thống kê cho dashboard của Farmer hiện tại */
  DashboardStatsResponse getFarmerDashboardStats(Authentication authentication);

  /** Lấy danh sách đơn hàng gần đây cho Farmer */
  List<OrderSummaryResponse> getRecentFarmerOrders(Authentication authentication, int limit);

  /** Lấy sản phẩm bán chạy nhất của Farmer */
  List<TopProductResponse> getTopSellingFarmerProducts(Authentication authentication, int limit);

  //  PHƯƠNG THỨC MỚI CHO FARMER CHART
  /** Lấy dữ liệu số lượng đơn hàng theo ngày cho biểu đồ Farmer */
  List<FarmerChartDataResponse> getFarmerOrderCountChartData(
      Authentication authentication, LocalDate startDate, LocalDate endDate);

  /** Lấy dữ liệu doanh thu theo ngày cho biểu đồ Farmer */
  List<FarmerChartDataResponse> getFarmerRevenueChartData(
      Authentication authentication, LocalDate startDate, LocalDate endDate);

  /** Lấy thống kê tổng quan cho dashboard Admin */

  /** Lấy thống kê tổng quan cho dashboard Admin */
  DashboardStatsResponse getAdminDashboardStats();

  /** Lấy dữ liệu doanh thu theo ngày cho biểu đồ Admin */
  List<TimeSeriesDataPoint<BigDecimal>> getDailyRevenueForAdminChart(
      LocalDate startDate, LocalDate endDate);

  /** Lấy dữ liệu số lượng đơn hàng theo ngày cho biểu đồ Admin */
  List<TimeSeriesDataPoint<Long>> getDailyOrderCountForAdminChart(
      LocalDate startDate, LocalDate endDate);

  /** Lấy danh sách các hoạt động gần đây cho Admin */
  List<RecentActivityResponse> getRecentActivitiesForAdmin(int limit);

  /** Lấy số lượng các mục chờ duyệt */
  Map<String, Long> getPendingApprovalCounts(); // Trả về Map cho tiện

  List<FarmerSummaryResponse> getTopPerformingFarmers(int limit);

  List<UserResponse> getTopSpendingBuyers(int limit);

  List<TimeSeriesDataPoint<Long>> getDailyUserRegistrations(LocalDate startDate, LocalDate endDate);
}
