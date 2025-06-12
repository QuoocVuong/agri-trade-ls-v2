package com.yourcompany.agritrade.catalog.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.request.ProductRejectRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.config.TestSecurityConfig;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCatalogController.class)
@Import(TestSecurityConfig.class)
@WithMockUser(roles = "ADMIN") // Apply to all tests in this class
class AdminCatalogControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private CategoryService categoryService;

  @MockBean private ProductService productService;

  private CategoryRequest categoryRequest;
  private CategoryResponse categoryResponse;
  private ProductDetailResponse productDetailResponse;
  private Page<ProductSummaryResponse> productSummaryPage;

  @BeforeEach
  void setUp() {
    categoryRequest = new CategoryRequest();
    categoryRequest.setName("Test Category");
    categoryRequest.setDescription("Test Description");

    categoryResponse = new CategoryResponse();
    categoryResponse.setId(1);
    categoryResponse.setName("Test Category");
    categoryResponse.setSlug("test-category");

    productDetailResponse = new ProductDetailResponse();
    productDetailResponse.setId(1L);
    productDetailResponse.setName("Test Product");

    ProductSummaryResponse productSummary = new ProductSummaryResponse();
    productSummary.setId(1L);
    productSummary.setName("Test Product Summary");
    productSummaryPage = new PageImpl<>(List.of(productSummary), PageRequest.of(0, 20), 1);
  }

  @Nested
  @DisplayName("Category Management Tests")
  class CategoryManagementTests {

    @Test
    @DisplayName("POST /api/admin/categories - Create Category - Success")
    void createCategory_success() throws Exception {
      when(categoryService.createCategory(any(CategoryRequest.class))).thenReturn(categoryResponse);

      mockMvc
          .perform(
              post("/api/admin/categories")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(categoryRequest)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Category created successfully")))
          .andExpect(jsonPath("$.data.name", is(categoryResponse.getName())));

      verify(categoryService).createCategory(any(CategoryRequest.class));
    }

    @Test
    @DisplayName("POST /api/admin/categories - Create Category - Validation Error")
    void createCategory_validationError() throws Exception {
      CategoryRequest invalidRequest = new CategoryRequest(); // Missing name

      mockMvc
          .perform(
              post("/api/admin/categories")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Validation Failed")))
          .andExpect(jsonPath("$.details.name", is("Category name is required")));

      verify(categoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("PUT /api/admin/categories/{id} - Update Category - Success")
    void updateCategory_success() throws Exception {
      when(categoryService.updateCategory(eq(1), any(CategoryRequest.class)))
          .thenReturn(categoryResponse);

      mockMvc
          .perform(
              put("/api/admin/categories/{id}", 1)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(categoryRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Category updated successfully")))
          .andExpect(jsonPath("$.data.name", is(categoryResponse.getName())));
    }

    @Test
    @DisplayName("PUT /api/admin/categories/{id} - Update Category - Not Found")
    void updateCategory_notFound() throws Exception {
      when(categoryService.updateCategory(eq(99), any(CategoryRequest.class)))
          .thenThrow(new ResourceNotFoundException("Category", "id", 99));

      mockMvc
          .perform(
              put("/api/admin/categories/{id}", 99)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(categoryRequest)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Category not found with id : '99'")));
    }

    @Test
    @DisplayName("DELETE /api/admin/categories/{id} - Delete Category - Success")
    void deleteCategory_success() throws Exception {
      doNothing().when(categoryService).deleteCategory(1);

      mockMvc
          .perform(delete("/api/admin/categories/{id}", 1))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Category deleted successfully")));
    }

    @Test
    @DisplayName("DELETE /api/admin/categories/{id} - Delete Category - Conflict (In Use)")
    void deleteCategory_conflict() throws Exception {
      doThrow(new BadRequestException("Category is in use"))
          .when(categoryService)
          .deleteCategory(1);

      mockMvc
          .perform(delete("/api/admin/categories/{id}", 1))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.success", is(false)))
          .andExpect(jsonPath("$.message", is("Category is in use")));
    }
  }

  @Nested
  @DisplayName("Product Management & Approval Tests")
  class ProductManagementTests {

    @Test
    @DisplayName("GET /api/admin/products - Get All Products - Success")
    void getAllProductsForAdmin_success() throws Exception {
      when(productService.getAllProductsForAdmin(any(), any(), any(), any(), any(Pageable.class)))
          .thenReturn(productSummaryPage);

      mockMvc
          .perform(get("/api/admin/products").param("page", "0").param("size", "20"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.content", hasSize(1)))
          .andExpect(jsonPath("$.data.content[0].name", is("Test Product Summary")));
    }

    @Test
    @DisplayName("GET /api/admin/products/{id} - Get Product By Id - Success")
    void getProductByIdForAdmin_success() throws Exception {
      when(productService.getProductByIdForAdmin(1L)).thenReturn(productDetailResponse);

      mockMvc
          .perform(get("/api/admin/products/{id}", 1L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.data.name", is(productDetailResponse.getName())));
    }

    @Test
    @DisplayName("POST /api/admin/products/{id}/approve - Approve Product - Success")
    void approveProduct_success() throws Exception {
      when(productService.approveProduct(1L)).thenReturn(productDetailResponse);

      mockMvc
          .perform(post("/api/admin/products/{id}/approve", 1L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product approved successfully")))
          .andExpect(jsonPath("$.data.name", is(productDetailResponse.getName())));
    }

    @Test
    @DisplayName("POST /api/admin/products/{id}/reject - Reject Product with Reason - Success")
    void rejectProduct_withReason_success() throws Exception {
      ProductRejectRequest rejectRequest = new ProductRejectRequest();
      rejectRequest.setReason("Not suitable");
      when(productService.rejectProduct(eq(1L), eq("Not suitable")))
          .thenReturn(productDetailResponse);

      mockMvc
          .perform(
              post("/api/admin/products/{id}/reject", 1L)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(rejectRequest)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product rejected successfully")));
    }

    @Test
    @DisplayName("POST /api/admin/products/{id}/reject - Reject Product without Reason - Success")
    void rejectProduct_withoutReason_success() throws Exception {
      when(productService.rejectProduct(eq(1L), isNull())).thenReturn(productDetailResponse);

      mockMvc
          .perform(
              post("/api/admin/products/{id}/reject", 1L)
                  .contentType(MediaType.APPLICATION_JSON) // Send empty body or null
                  .content(objectMapper.writeValueAsString(null))) // Hoặc "{}"
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product rejected successfully")));
    }

    @Test
    @DisplayName("DELETE /api/admin/products/{id}/force - Force Delete Product - Success")
    void forceDeleteProduct_success() throws Exception {
      doNothing().when(productService).forceDeleteProduct(1L);

      mockMvc
          .perform(delete("/api/admin/products/{id}/force", 1L))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.success", is(true)))
          .andExpect(jsonPath("$.message", is("Product permanently deleted")));
    }
  }
}
