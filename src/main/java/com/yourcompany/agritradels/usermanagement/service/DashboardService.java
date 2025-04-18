package com.yourcompany.agritradels.usermanagement.service;

import com.yourcompany.agritradels.catalog.dto.response.TopProductResponse; // Import
import com.yourcompany.agritradels.ordering.dto.response.OrderSummaryResponse; // Import
import com.yourcompany.agritradels.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.RecentActivityResponse;
import com.yourcompany.agritradels.usermanagement.dto.response.TimeSeriesDataPoint;
import org.springframework.data.domain.Pageable; // Import
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List; // Import
import java.util.Map;

public interface DashboardService {

    /** Lấy thống kê cho dashboard của Farmer hiện tại */
    DashboardStatsResponse getFarmerDashboardStats(Authentication authentication);

    /** Lấy danh sách đơn hàng gần đây cho Farmer */
    List<OrderSummaryResponse> getRecentFarmerOrders(Authentication authentication, int limit);

    /** Lấy sản phẩm bán chạy nhất của Farmer */
    List<TopProductResponse> getTopSellingFarmerProducts(Authentication authentication, int limit);


    /** Lấy thống kê tổng quan cho dashboard Admin */
    //DashboardStatsResponse getAdminDashboardStats();

    // Có thể thêm các phương thức lấy dữ liệu biểu đồ, hoạt động gần đây... cho Admin



    /** Lấy thống kê tổng quan cho dashboard Admin */
    DashboardStatsResponse getAdminDashboardStats();

    /** Lấy dữ liệu doanh thu theo ngày cho biểu đồ Admin */
    List<TimeSeriesDataPoint<BigDecimal>> getDailyRevenueForAdminChart(LocalDate startDate, LocalDate endDate);

    /** Lấy dữ liệu số lượng đơn hàng theo ngày cho biểu đồ Admin */
    List<TimeSeriesDataPoint<Long>> getDailyOrderCountForAdminChart(LocalDate startDate, LocalDate endDate);

    /** Lấy danh sách các hoạt động gần đây cho Admin */
    List<RecentActivityResponse> getRecentActivitiesForAdmin(int limit);

    /** Lấy số lượng các mục chờ duyệt */
    Map<String, Long> getPendingApprovalCounts(); // Trả về Map cho tiện
}