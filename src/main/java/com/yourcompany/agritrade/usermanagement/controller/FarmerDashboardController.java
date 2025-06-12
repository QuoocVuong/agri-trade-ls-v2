package com.yourcompany.agritrade.usermanagement.controller; // Đặt trong usermanagement hoặc module

// dashboard riêng

import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerChartDataResponse;
import com.yourcompany.agritrade.usermanagement.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farmer/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARMER')")
public class FarmerDashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats(
      Authentication authentication) {
    DashboardStatsResponse stats = dashboardService.getFarmerDashboardStats(authentication);
    return ResponseEntity.ok(ApiResponse.success(stats));
  }

  @GetMapping("/recent-orders")
  public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> getRecentOrders(
      Authentication authentication,
      @RequestParam(defaultValue = "5") int limit) { // Lấy 5 đơn gần nhất mặc định
    List<OrderSummaryResponse> orders =
        dashboardService.getRecentFarmerOrders(
            authentication, Math.max(1, limit)); // Đảm bảo limit > 0
    return ResponseEntity.ok(ApiResponse.success(orders));
  }

  @GetMapping("/top-products")
  public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
      Authentication authentication,
      @RequestParam(defaultValue = "5") int limit) { // Lấy 5 sản phẩm bán chạy nhất
    List<TopProductResponse> products =
        dashboardService.getTopSellingFarmerProducts(authentication, Math.max(1, limit));
    return ResponseEntity.ok(ApiResponse.success(products));
  }

  // ===== ENDPOINT MỚI CHO BIỂU ĐỒ SỐ LƯỢNG ĐƠN HÀNG =====
  @GetMapping("/order-count-chart")
  public ResponseEntity<ApiResponse<List<FarmerChartDataResponse>>> getOrderCountChartData(
      Authentication authentication,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    List<FarmerChartDataResponse> chartData =
        dashboardService.getFarmerOrderCountChartData(authentication, startDate, endDate);

    return ResponseEntity.ok(ApiResponse.success(chartData));
  }

  // ===== ENDPOINT MỚI CHO BIỂU ĐỒ DOANH THU =====
  @GetMapping("/revenue-chart")
  public ResponseEntity<ApiResponse<List<FarmerChartDataResponse>>> getRevenueChartData(
      Authentication authentication,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

    List<FarmerChartDataResponse> chartData =
        dashboardService.getFarmerRevenueChartData(authentication, startDate, endDate);

    return ResponseEntity.ok(ApiResponse.success(chartData));
  }
}
