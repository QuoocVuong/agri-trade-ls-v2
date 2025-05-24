package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.RecentActivityResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.TimeSeriesDataPoint;
import com.yourcompany.agritrade.usermanagement.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerUnitTest {

    @Mock private DashboardService dashboardService;

    @InjectMocks
    private AdminDashboardController adminDashboardController;

    private DashboardStatsResponse dashboardStatsResponse;
    private List<TimeSeriesDataPoint<BigDecimal>> revenueChartData;
    // ... các DTO response mẫu khác

    @BeforeEach
    void setUp() {
        dashboardStatsResponse = DashboardStatsResponse.builder().totalUsers(100L).build(); // Ví dụ
        revenueChartData = Collections.singletonList(new TimeSeriesDataPoint<>(LocalDate.now(), BigDecimal.TEN));
        // ...
    }

    @Test
    void getStats_success_returnsOkWithStats() {
        when(dashboardService.getAdminDashboardStats()).thenReturn(dashboardStatsResponse);

        ResponseEntity<ApiResponse<DashboardStatsResponse>> responseEntity = adminDashboardController.getStats();

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(dashboardStatsResponse, responseEntity.getBody().getData());
        verify(dashboardService).getAdminDashboardStats();
    }

    @Test
    void getRevenueChartData_success_returnsOkWithChartData() {
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        when(dashboardService.getDailyRevenueForAdminChart(startDate, endDate)).thenReturn(revenueChartData);

        ResponseEntity<ApiResponse<List<TimeSeriesDataPoint<BigDecimal>>>> responseEntity =
                adminDashboardController.getRevenueChartData(startDate, endDate);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(revenueChartData, responseEntity.getBody().getData());
        verify(dashboardService).getDailyRevenueForAdminChart(startDate, endDate);
    }

    // TODO: Thêm test cho getOrderCountChartData, getRecentActivities, getPendingApprovalCounts
}