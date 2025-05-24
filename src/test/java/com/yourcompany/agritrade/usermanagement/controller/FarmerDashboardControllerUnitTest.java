package com.yourcompany.agritrade.usermanagement.controller; // Hoặc package của controller

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.catalog.dto.response.TopProductResponse; // Giả sử DTO này tồn tại
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse; // Giả sử DTO này tồn tại
import com.yourcompany.agritrade.usermanagement.dto.response.DashboardStatsResponse;
import com.yourcompany.agritrade.usermanagement.dto.response.FarmerChartDataResponse;
import com.yourcompany.agritrade.usermanagement.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmerDashboardControllerUnitTest {

    @Mock private DashboardService dashboardService;
    @Mock private Authentication authentication; // Mock Authentication object

    @InjectMocks
    private FarmerDashboardController farmerDashboardController;

    private DashboardStatsResponse dashboardStatsResponse;
    private List<OrderSummaryResponse> recentOrders;
    private List<TopProductResponse> topProducts;
    private List<FarmerChartDataResponse> chartData;

    @BeforeEach
    void setUp() {
        // Khởi tạo dữ liệu mẫu cho các response DTO
        dashboardStatsResponse = DashboardStatsResponse.builder()
                .totalOrdersToday(5L)
                .totalRevenueThisMonth(new BigDecimal("1500000"))
                .build(); // Thêm các trường khác nếu cần

        OrderSummaryResponse orderSummary = new OrderSummaryResponse(); // Khởi tạo order summary mẫu
        orderSummary.setId(1L);
        orderSummary.setOrderCode("FARMER-ORD-001");
        recentOrders = Collections.singletonList(orderSummary);

        TopProductResponse topProduct = new TopProductResponse(); // Giả sử constructor
        topProducts = Collections.singletonList(topProduct);

        chartData = Collections.singletonList(new FarmerChartDataResponse(LocalDate.now(), 10L)); // Ví dụ dữ liệu chart

        // Mock hành vi mặc định của Authentication nếu cần cho tất cả các test
        // when(authentication.getName()).thenReturn("farmer@example.com");
    }

    @Test
    void getStats_success_returnsOkWithDashboardStats() {
        // Arrange
        when(dashboardService.getFarmerDashboardStats(authentication)).thenReturn(dashboardStatsResponse);

        // Act
        ResponseEntity<ApiResponse<DashboardStatsResponse>> responseEntity =
                farmerDashboardController.getStats(authentication);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(dashboardStatsResponse, responseEntity.getBody().getData());
        verify(dashboardService).getFarmerDashboardStats(authentication);
    }

    @Test
    void getRecentOrders_success_returnsOkWithOrderList() {
        // Arrange
        int limit = 5;
        when(dashboardService.getRecentFarmerOrders(authentication, limit)).thenReturn(recentOrders);

        // Act
        ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> responseEntity =
                farmerDashboardController.getRecentOrders(authentication, limit);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(recentOrders, responseEntity.getBody().getData());
        verify(dashboardService).getRecentFarmerOrders(authentication, limit);
    }

    @Test
    void getRecentOrders_withDefaultLimit_callsServiceWithDefaultLimit() {
        // Arrange
        int defaultLimit = 5; // Giá trị defaultValue trong @RequestParam
        when(dashboardService.getRecentFarmerOrders(authentication, defaultLimit)).thenReturn(recentOrders);

        // Act
        farmerDashboardController.getRecentOrders(authentication, defaultLimit); // Truyền giá trị mặc định

        // Assert
        verify(dashboardService).getRecentFarmerOrders(authentication, defaultLimit);
    }


    @Test
    void getTopProducts_success_returnsOkWithProductList() {
        // Arrange
        int limit = 3;
        when(dashboardService.getTopSellingFarmerProducts(authentication, limit)).thenReturn(topProducts);

        // Act
        ResponseEntity<ApiResponse<List<TopProductResponse>>> responseEntity =
                farmerDashboardController.getTopProducts(authentication, limit);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(topProducts, responseEntity.getBody().getData());
        verify(dashboardService).getTopSellingFarmerProducts(authentication, limit);
    }

    @Test
    void getOrderCountChartData_success_returnsOkWithChartData() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        when(dashboardService.getFarmerOrderCountChartData(authentication, startDate, endDate)).thenReturn(chartData);

        // Act
        ResponseEntity<ApiResponse<List<FarmerChartDataResponse>>> responseEntity =
                farmerDashboardController.getOrderCountChartData(authentication, startDate, endDate);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(chartData, responseEntity.getBody().getData());
        verify(dashboardService).getFarmerOrderCountChartData(authentication, startDate, endDate);
    }

    @Test
    void getRevenueChartData_success_returnsOkWithChartData() {
        // Arrange
        LocalDate startDate = LocalDate.now().minusMonths(1);
        LocalDate endDate = LocalDate.now();
        List<FarmerChartDataResponse> revenueData = Collections.singletonList(new FarmerChartDataResponse(LocalDate.now().toString(), new BigDecimal("500000")));
        when(dashboardService.getFarmerRevenueChartData(authentication, startDate, endDate)).thenReturn(revenueData);

        // Act
        ResponseEntity<ApiResponse<List<FarmerChartDataResponse>>> responseEntity =
                farmerDashboardController.getRevenueChartData(authentication, startDate, endDate);

        // Assert
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(revenueData, responseEntity.getBody().getData());
        verify(dashboardService).getFarmerRevenueChartData(authentication, startDate, endDate);
    }

    // TODO: Thêm các test case cho trường hợp lỗi (ví dụ: service ném exception)
    // Ví dụ:
    // @Test
    // void getStats_serviceThrowsException_returnsErrorResponse() {
    //     when(dashboardService.getFarmerDashboardStats(authentication)).thenThrow(new RuntimeException("Service error"));
    //
    //     // Unit test controller thường không bắt exception này trực tiếp nếu có @ControllerAdvice
    //     // Thay vào đó, bạn có thể kiểm tra xem controller có gọi service không,
    //     // và trong integration test, bạn kiểm tra @ControllerAdvice.
    //     // Hoặc nếu controller có try-catch riêng:
    //     // ResponseEntity<ApiResponse<DashboardStatsResponse>> responseEntity = farmerDashboardController.getStats(authentication);
    //     // assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
    //     // assertFalse(responseEntity.getBody().isSuccess());
    // }
}