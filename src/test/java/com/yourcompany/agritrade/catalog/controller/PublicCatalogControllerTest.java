// src/test/java/com/yourcompany/agritrade/catalog/controller/PublicCatalogControllerTest.java
package com.yourcompany.agritrade.catalog.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicCatalogController.class)
@Import(TestSecurityConfig.class) // <<< THÊM DÒNG NÀY
class PublicCatalogControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean // Mock các service mà controller này phụ thuộc
  private CategoryService categoryService;

  @MockBean private ProductService productService;

  // ObjectMapper được Spring Boot tự động cung cấp khi dùng @WebMvcTest
  @Autowired private ObjectMapper objectMapper;

  private CategoryResponse categoryResponse;
  private ProductSummaryResponse productSummaryResponse;
  private ProductDetailResponse productDetailResponse;

  @BeforeEach
  void setUp() {
    categoryResponse = new CategoryResponse();
    categoryResponse.setId(1);
    categoryResponse.setName("Rau Củ");
    categoryResponse.setSlug("rau-cu");

    productSummaryResponse = new ProductSummaryResponse();
    productSummaryResponse.setId(1L);
    productSummaryResponse.setName("Cà Rốt");
    productSummaryResponse.setPrice(new BigDecimal("15000"));

    productDetailResponse = new ProductDetailResponse();
    productDetailResponse.setId(1L);
    productDetailResponse.setName("Cà Rốt Chi Tiết");
    productDetailResponse.setDescription("Mô tả chi tiết cà rốt");
  }

  // ... các test case còn lại giữ nguyên ...
  @Nested
  @DisplayName("Category Endpoints Tests")
  class CategoryEndpoints {
    @Test
    @DisplayName("GET /api/public/categories - should return list of categories")
    void getAllCategoriesForDropdown_returnsListOfCategories() throws Exception {
      when(categoryService.getAllCategoriesForDropdown()).thenReturn(List.of(categoryResponse));

      mockMvc
          .perform(get("/api/public/categories").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data", hasSize(1)))
          .andExpect(jsonPath("$.data[0].name", is(categoryResponse.getName())));
    }

    @Test
    @DisplayName("GET /api/public/categories/tree - should return category tree")
    void getCategoryTree_returnsCategoryTree() throws Exception {
      when(categoryService.getCategoryTree())
          .thenReturn(List.of(categoryResponse)); // Giả sử tree đơn giản

      mockMvc
          .perform(get("/api/public/categories/tree").contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data[0].name", is(categoryResponse.getName())));
    }

    @Test
    @DisplayName("GET /api/public/categories/{slug} - category found")
    void getCategoryBySlug_categoryFound_returnsCategory() throws Exception {
      String slug = "rau-cu";
      when(categoryService.getCategoryBySlug(slug)).thenReturn(categoryResponse);

      mockMvc
          .perform(
              get("/api/public/categories/{slug}", slug).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.slug", is(slug)));
    }

    @Test
    @DisplayName("GET /api/public/categories/{slug} - category not found")
    void getCategoryBySlug_categoryNotFound_returnsNotFound() throws Exception {
      String slug = "khong-ton-tai";
      when(categoryService.getCategoryBySlug(slug))
          .thenThrow(new ResourceNotFoundException("Category", "slug", slug));

      mockMvc
          .perform(
              get("/api/public/categories/{slug}", slug).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)));
    }
  }

  @Nested
  @DisplayName("Product Endpoints Tests")
  class ProductEndpoints {
    @Test
    @DisplayName("GET /api/public/products - search with all filters")
    void searchProducts_withAllFilters_returnsPaginatedProducts() throws Exception {
      Pageable pageable = PageRequest.of(0, 12);
      Page<ProductSummaryResponse> productPage =
          new PageImpl<>(List.of(productSummaryResponse), pageable, 1);
      String keyword = "cà";
      Integer categoryId = 1;
      String provinceCode = "20";
      Double minPrice = 10000.0;
      Double maxPrice = 20000.0;
      Integer minRating = 4;

      when(productService.searchPublicProducts(
              eq(keyword),
              eq(categoryId),
              eq(provinceCode),
              eq(minPrice),
              eq(maxPrice),
              eq(minRating),
              any(Pageable.class)))
          .thenReturn(productPage);

      mockMvc
          .perform(
              get("/api/public/products")
                  .param("keyword", keyword)
                  .param("categoryId", categoryId.toString())
                  .param("provinceCode", provinceCode)
                  .param("minPrice", minPrice.toString())
                  .param("maxPrice", maxPrice.toString())
                  .param("minRating", minRating.toString())
                  .param("page", "0")
                  .param("size", "12")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));

      verify(productService)
          .searchPublicProducts(
              eq(keyword),
              eq(categoryId),
              eq(provinceCode),
              eq(minPrice),
              eq(maxPrice),
              eq(minRating),
              any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/public/products - search with no filters")
    void searchProducts_noFilters_returnsPaginatedProducts() throws Exception {
      Pageable pageable = PageRequest.of(0, 12);
      Page<ProductSummaryResponse> productPage =
          new PageImpl<>(List.of(productSummaryResponse), pageable, 1);

      when(productService.searchPublicProducts(
              isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
          .thenReturn(productPage);

      mockMvc
          .perform(
              get("/api/public/products")
                  .param("page", "0")
                  .param("size", "12")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));
    }

    @Test
    @DisplayName("GET /api/public/products/{slug} - product found")
    void getProductBySlug_productFound_returnsProductDetail() throws Exception {
      String slug = "ca-rot-chi-tiet";
      productDetailResponse.setSlug(slug); // Đảm bảo DTO có slug để so sánh
      when(productService.getPublicProductBySlug(slug)).thenReturn(productDetailResponse);

      mockMvc
          .perform(get("/api/public/products/{slug}", slug).contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.slug", is(slug)));
    }

    @Test
    @DisplayName("GET /api/public/products/id/{id} - product found")
    void getProductById_productFound_returnsProductDetail() throws Exception {
      Long productId = 1L;
      when(productService.getPublicProductById(productId)).thenReturn(productDetailResponse);

      mockMvc
          .perform(
              get("/api/public/products/id/{id}", productId)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.id", is(productId.intValue())));
    }

    @Test
    @DisplayName("GET /api/public/farmer/{farmerId}/products - success")
    void getProductsByFarmerPublic_success_returnsPaginatedProducts() throws Exception {
      Long farmerId = 100L;
      Pageable pageable = PageRequest.of(0, 8);
      Page<ProductSummaryResponse> productPage =
          new PageImpl<>(List.of(productSummaryResponse), pageable, 1);

      when(productService.getPublicProductsByFarmerId(eq(farmerId), any(Pageable.class)))
          .thenReturn(productPage);

      mockMvc
          .perform(
              get("/api/public/farmer/{farmerId}/products", farmerId)
                  .param("page", "0")
                  .param("size", "8")
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].name", is(productSummaryResponse.getName())));

      verify(productService).getPublicProductsByFarmerId(eq(farmerId), any(Pageable.class));
    }
  }
}
