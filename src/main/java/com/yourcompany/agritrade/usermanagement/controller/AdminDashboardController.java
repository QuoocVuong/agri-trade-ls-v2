package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.RecentActivityResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.TimeSeriesDataPoint;
import com.yourcompany.agritrade.usermanagement.service.DashboardService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/stats")
  public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
    DashboardStatsResponse stats = dashboardService.getAdminDashboardStats();
    return ResponseEntity.ok(ApiResponse.success(stats));
  }

  @GetMapping("/revenue-chart")
  public ResponseEntity<ApiResponse<List<TimeSeriesDataPoint<BigDecimal>>>> getRevenueChartData(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    List<TimeSeriesDataPoint<BigDecimal>> data =
        dashboardService.getDailyRevenueForAdminChart(startDate, endDate);
    return ResponseEntity.ok(ApiResponse.success(data));
  }

  @GetMapping("/order-count-chart")
  public ResponseEntity<ApiResponse<List<TimeSeriesDataPoint<Long>>>> getOrderCountChartData(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    List<TimeSeriesDataPoint<Long>> data =
        dashboardService.getDailyOrderCountForAdminChart(startDate, endDate);
    return ResponseEntity.ok(ApiResponse.success(data));
  }

  @GetMapping("/recent-activities")
  public ResponseEntity<ApiResponse<List<RecentActivityResponse>>> getRecentActivities(
      @RequestParam(defaultValue = "10") int limit) {
    List<RecentActivityResponse> activities =
        dashboardService.getRecentActivitiesForAdmin(Math.max(1, limit));
    return ResponseEntity.ok(ApiResponse.success(activities));
  }

  @GetMapping("/pending-approvals")
  public ResponseEntity<ApiResponse<Map<String, Long>>> getPendingApprovalCounts() {
    Map<String, Long> counts = dashboardService.getPendingApprovalCounts();
    return ResponseEntity.ok(ApiResponse.success(counts));
  }
}
