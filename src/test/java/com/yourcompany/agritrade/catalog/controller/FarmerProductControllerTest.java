package com.yourcompany.agritrade.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import com.yourcompany.agritrade.config.properties.JwtProperties;
import com.yourcompany.agritrade.config.security.JwtTokenProvider;
import com.yourcompany.agritrade.config.security.TokenBlacklistService;
import com.yourcompany.agritrade.config.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FarmerProductController.class)
// Chỉ test FarmerProductController, không load toàn bộ context
// Cần có SecurityConfig trong context để @PreAuthorize hoạt động,
// hoặc mock SecurityContext nếu không muốn load SecurityConfig.
// @WithMockUser giả lập user đã đăng nhập với vai trò FARMER.
@WithMockUser(username = "farmer@example.com", roles = {"FARMER"})
@Import(TestSecurityConfig.class)
class FarmerProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;



    @Autowired
    private ObjectMapper objectMapper; // Spring Boot tự động cấu hình bean này

    private ProductSummaryResponse productSummaryResponse;
    private ProductDetailResponse productDetailResponse;
    private ProductRequest productRequest;
    //private Authentication mockAuthentication; // Sẽ được cung cấp bởi @WithMockUser

    @BeforeEach
    void setUp() {
        // mockAuthentication sẽ được tự động tạo bởi @WithMockUser
        // Nếu cần truy cập trực tiếp, có thể lấy từ SecurityContextHolder trong test
        // hoặc truyền vào như một tham số (khó hơn với MockMvc)

        productSummaryResponse = new ProductSummaryResponse();
        productSummaryResponse.setId(1L);
        productSummaryResponse.setName("Test Product Summary");
        // ... các trường khác

        productDetailResponse = new ProductDetailResponse();
        productDetailResponse.setId(1L);
        productDetailResponse.setName("Test Product Detail");
        productDetailResponse.setPrice(new BigDecimal("100.00"));
        // ... các trường khác

        productRequest = new ProductRequest();
        productRequest.setName("New Valid Product");
        productRequest.setCategoryId(1);
        productRequest.setUnit("kg");
        productRequest.setPrice(new BigDecimal("150.00"));
        productRequest.setStockQuantity(10);
        // ... các trường khác

        // Mock các giá trị cho JwtProperties
//        JwtProperties.RefreshToken mockRefreshTokenProps = new JwtProperties.RefreshToken();
//        mockRefreshTokenProps.setExpirationMs(TimeUnit.DAYS.toMillis(7));
//        // Sử dụng lenient() vì các mock này có thể không được dùng trong mọi test case



}

    @Nested
    @DisplayName("GET /api/farmer/products/me")
    class GetMyProductsTests {
        @Test
        @DisplayName("should return paginated products when no filters")
        void getMyProducts_noFilters_returnsPaginatedProducts() throws Exception {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ProductSummaryResponse> productPage = new PageImpl<>(List.of(productSummaryResponse), pageable, 1);
            when(productService.getMyProducts(any(Authentication.class), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(productPage);

            mockMvc.perform(get("/api/farmer/products/me")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));

            verify(productService).getMyProducts(any(Authentication.class), isNull(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("should return paginated products with keyword and valid status filter")
        void getMyProducts_withKeywordAndValidStatus_returnsFilteredProducts() throws Exception {
            Pageable pageable = PageRequest.of(0, 5);
            Page<ProductSummaryResponse> productPage = new PageImpl<>(List.of(productSummaryResponse), pageable, 1);
            String keyword = "Test";
            String statusStr = "PUBLISHED";
            ProductStatus expectedStatusEnum = ProductStatus.PUBLISHED;

            when(productService.getMyProducts(any(Authentication.class), eq(keyword), eq(expectedStatusEnum), any(Pageable.class)))
                    .thenReturn(productPage);

            mockMvc.perform(get("/api/farmer/products/me")
                            .param("keyword", keyword)
                            .param("status", statusStr)
                            .param("size", "5")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));

            verify(productService).getMyProducts(any(Authentication.class), eq(keyword), eq(expectedStatusEnum), any(Pageable.class));
        }

        @Test
        @DisplayName("should handle invalid status string gracefully")
        void getMyProducts_withInvalidStatus_callsServiceWithNullStatus() throws Exception {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ProductSummaryResponse> productPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            String invalidStatusStr = "INVALID_STATUS";

            when(productService.getMyProducts(any(Authentication.class), isNull(), isNull(), any(Pageable.class)))
                    .thenReturn(productPage);

            mockMvc.perform(get("/api/farmer/products/me")
                            .param("status", invalidStatusStr)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(0)));

            verify(productService).getMyProducts(any(Authentication.class), isNull(), isNull(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /api/farmer/products/me/{id}")
    class GetMyProductByIdTests {
        @Test
        @DisplayName("should return product detail when product exists")
        void getMyProductById_productExists_returnsProductDetail() throws Exception {
            Long productId = 1L;
            when(productService.getMyProductById(any(Authentication.class), eq(productId)))
                    .thenReturn(productDetailResponse);

            mockMvc.perform(get("/api/farmer/products/me/{id}", productId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.name", is(productDetailResponse.getName())));
        }

        @Test
        @DisplayName("should return 404 when product not found (simulated by service)")
        void getMyProductById_productNotFound_returnsNotFound() throws Exception {
            Long productId = 99L;
            when(productService.getMyProductById(any(Authentication.class), eq(productId)))
                    .thenThrow(new ResourceNotFoundException("Product", "id", productId));

            mockMvc.perform(get("/api/farmer/products/me/{id}", productId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()) // GlobalExceptionHandler sẽ xử lý
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Product not found with id : '99'")));
        }
    }

    @Nested
    @DisplayName("POST /api/farmer/products/me")
    class CreateMyProductTests {
        @Test
        @DisplayName("should create product and return 201 Created")
        void createMyProduct_validRequest_returnsCreated() throws Exception {
            when(productService.createMyProduct(any(Authentication.class), any(ProductRequest.class)))
                    .thenReturn(productDetailResponse);

            mockMvc.perform(post("/api/farmer/products/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(productRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Product created successfully")))
                    .andExpect(jsonPath("$.data.name", is(productDetailResponse.getName())));
        }

        @Test
        @DisplayName("should return 400 Bad Request for invalid product request")
        void createMyProduct_invalidRequest_returnsBadRequest() throws Exception {
            ProductRequest invalidRequest = new ProductRequest(); // Thiếu các trường bắt buộc
            // Không cần mock productService vì validation sẽ fail trước đó

            mockMvc.perform(post("/api/farmer/products/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Validation Failed")))
                    .andExpect(jsonPath("$.details.name", is("Product name is required"))) // Ví dụ lỗi validation
                    .andExpect(jsonPath("$.details.categoryId", is("Category ID is required")));
        }
    }

    @Nested
    @DisplayName("PUT /api/farmer/products/me/{id}")
    class UpdateMyProductTests {
        @Test
        @DisplayName("should update product and return 200 OK")
        void updateMyProduct_validRequest_returnsOk() throws Exception {
            Long productId = 1L;
            when(productService.updateMyProduct(any(Authentication.class), eq(productId), any(ProductRequest.class)))
                    .thenReturn(productDetailResponse);

            mockMvc.perform(put("/api/farmer/products/me/{id}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(productRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Product updated successfully")))
                    .andExpect(jsonPath("$.data.name", is(productDetailResponse.getName())));
        }
    }

    @Nested
    @DisplayName("DELETE /api/farmer/products/me/{id}")
    class DeleteMyProductTests {
        @Test
        @DisplayName("should delete product and return 200 OK")
        void deleteMyProduct_validId_returnsOk() throws Exception {
            Long productId = 1L;
            // Không cần mock authentication ở đây vì @WithMockUser đã xử lý
            doNothing().when(productService).deleteMyProduct(any(Authentication.class), eq(productId));

            mockMvc.perform(delete("/api/farmer/products/me/{id}", productId)
                            // .with(authentication(mockAuthentication)) // Không cần dòng này nữa
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("Product deleted successfully")));

            verify(productService).deleteMyProduct(any(Authentication.class), eq(productId));
        }
    }
}