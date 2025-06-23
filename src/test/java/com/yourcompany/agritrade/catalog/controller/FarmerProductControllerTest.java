package com.yourcompany.agritrade.catalog.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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

@WebMvcTest(FarmerProductController.class)
@WithMockUser(
    username = "farmer@example.com",
    roles = {"FARMER"})
@Import(TestSecurityConfig.class)
class FarmerProductControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private ProductService productService;
  @Autowired private ObjectMapper objectMapper;

  private ProductSummaryResponse productSummaryResponse;
  private ProductDetailResponse productDetailResponse;
  private ProductRequest productRequest;

  @BeforeEach
  void setUp() {
    productSummaryResponse = new ProductSummaryResponse();
    productSummaryResponse.setId(1L);
    productSummaryResponse.setName("Test Product Summary");

    productDetailResponse = new ProductDetailResponse();
    productDetailResponse.setId(1L);
    productDetailResponse.setName("Test Product Detail");
    productDetailResponse.setPrice(new BigDecimal("100.00"));

    productRequest = new ProductRequest();
    productRequest.setName("New Valid Product");
    productRequest.setCategoryId(1);
    productRequest.setUnit("kg");
    productRequest.setPrice(new BigDecimal("150.00"));
    productRequest.setStockQuantity(10);
  }

  @Nested
  @DisplayName("GET /api/farmer/products/me")
  class GetMyProductsTests {
    @Test
    @DisplayName("should return paginated products when no filters")
    void getMyProducts_noFilters_returnsPaginatedProducts() throws Exception {
      Pageable pageable = PageRequest.of(0, 10);
      Page<ProductSummaryResponse> productPage =
          new PageImpl<>(List.of(productSummaryResponse), pageable, 1);

      // SỬA LỖI: Mock đúng phương thức getMyB2CProducts
      when(productService.getMyB2CProducts(
              any(Authentication.class), isNull(), isNull(), any(Pageable.class)))
          .thenReturn(productPage);

      mockMvc
          .perform(
              get("/api/farmer/products/me")
                  .param("page", "0")
                  .param("size", "10")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));

      // SỬA LỖI: Verify đúng phương thức
      verify(productService)
          .getMyB2CProducts(any(Authentication.class), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("should return paginated products with keyword and valid status filter")
    void getMyProducts_withKeywordAndValidStatus_returnsFilteredProducts() throws Exception {
      Pageable pageable = PageRequest.of(0, 5);
      Page<ProductSummaryResponse> productPage =
          new PageImpl<>(List.of(productSummaryResponse), pageable, 1);
      String keyword = "Test";
      String statusStr = "PUBLISHED";
      ProductStatus expectedStatusEnum = ProductStatus.PUBLISHED;

      // SỬA LỖI: Mock đúng phương thức
      when(productService.getMyB2CProducts(
              any(Authentication.class), eq(keyword), eq(expectedStatusEnum), any(Pageable.class)))
          .thenReturn(productPage);

      mockMvc
          .perform(
              get("/api/farmer/products/me")
                  .param("keyword", keyword)
                  .param("status", statusStr)
                  .param("size", "5")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));

      // SỬA LỖI: Verify đúng phương thức
      verify(productService)
          .getMyB2CProducts(
              any(Authentication.class), eq(keyword), eq(expectedStatusEnum), any(Pageable.class));
    }

    @Test
    @DisplayName("should handle invalid status string gracefully")
    void getMyProducts_withInvalidStatus_callsServiceWithNullStatus() throws Exception {
      Pageable pageable = PageRequest.of(0, 10);
      Page<ProductSummaryResponse> productPage =
          new PageImpl<>(Collections.emptyList(), pageable, 0);
      String invalidStatusStr = "INVALID_STATUS";

      // SỬA LỖI: Mock đúng phương thức
      when(productService.getMyB2CProducts(
              any(Authentication.class), isNull(), isNull(), any(Pageable.class)))
          .thenReturn(productPage);

      mockMvc
          .perform(
              get("/api/farmer/products/me")
                  .param("status", invalidStatusStr)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.content", hasSize(0)));

      // SỬA LỖI: Verify đúng phương thức
      verify(productService)
          .getMyB2CProducts(any(Authentication.class), isNull(), isNull(), any(Pageable.class));
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

      mockMvc
          .perform(
              get("/api/farmer/products/me/{id}", productId)
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

      mockMvc
          .perform(
              get("/api/farmer/products/me/{id}", productId)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
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

      mockMvc
          .perform(
              post("/api/farmer/products/me")
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
      ProductRequest invalidRequest = new ProductRequest();

      mockMvc
          .perform(
              post("/api/farmer/products/me")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          // SỬA LỖI: Cập nhật message lỗi cho đúng
          .andExpect(jsonPath("$.message", is("Dữ liệu không hợp lệ")))
          .andExpect(jsonPath("$.details.name", is("Product name is required")))
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
      when(productService.updateMyProduct(
              any(Authentication.class), eq(productId), any(ProductRequest.class)))
          .thenReturn(productDetailResponse);

      mockMvc
          .perform(
              put("/api/farmer/products/me/{id}", productId)
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
      doNothing().when(productService).deleteMyProduct(any(Authentication.class), eq(productId));

      mockMvc
          .perform(
              delete("/api/farmer/products/me/{id}", productId)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product deleted successfully")));

      verify(productService).deleteMyProduct(any(Authentication.class), eq(productId));
    }
  }
}
