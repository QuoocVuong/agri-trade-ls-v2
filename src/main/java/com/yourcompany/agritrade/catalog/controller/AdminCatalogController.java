package com.yourcompany.agritrade.catalog.controller;

import com.yourcompany.agritrade.catalog.dto.request.CategoryRequest;
import com.yourcompany.agritrade.catalog.dto.request.ProductRejectRequest;
import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

  private final CategoryService categoryService;
  private final ProductService productService;

  // --- Category Management ---
  @PostMapping("/categories")
  public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
      @Valid @RequestBody CategoryRequest request) {
    CategoryResponse createdCategory = categoryService.createCategory(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(createdCategory, "Category created successfully"));
  }

  @PutMapping("/categories/{id}")
  public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
      @PathVariable Integer id, @Valid @RequestBody CategoryRequest request) {
    CategoryResponse updatedCategory = categoryService.updateCategory(id, request);
    return ResponseEntity.ok(ApiResponse.success(updatedCategory, "Category updated successfully"));
  }

  @DeleteMapping("/categories/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Integer id) {
    categoryService.deleteCategory(id);
    return ResponseEntity.ok(ApiResponse.success("Category deleted successfully"));
  }

  // --- Product Management & Approval ---
  @GetMapping("/products")
  public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getAllProductsForAdmin(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer categoryId,
      @RequestParam(required = false) Long farmerId,
      @PageableDefault(size = 20, sort = "createdAt,desc") Pageable pageable) {

    Page<ProductSummaryResponse> products =
        productService.getAllProductsForAdmin(keyword, status, categoryId, farmerId, pageable);
    return ResponseEntity.ok(ApiResponse.success(products));
  }

  @GetMapping("/products/{id}")
  public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductByIdForAdmin(
      @PathVariable Long id) {

    ProductDetailResponse product = productService.getProductByIdForAdmin(id);
    return ResponseEntity.ok(ApiResponse.success(product));
  }

  @PostMapping("/products/{id}/approve")
  public ResponseEntity<ApiResponse<ProductDetailResponse>> approveProduct(@PathVariable Long id) {

    ProductDetailResponse product = productService.approveProduct(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON) // Thêm Content-Type
        .body(ApiResponse.success(product, "Product approved successfully"));
  }

  @PostMapping("/products/{id}/reject")
  public ResponseEntity<ApiResponse<ProductDetailResponse>> rejectProduct(
      @PathVariable Long id, @RequestBody(required = false) ProductRejectRequest request) {
    String reason = (request != null) ? request.getReason() : null;

    ProductDetailResponse product = productService.rejectProduct(id, reason);
    return ResponseEntity.ok(ApiResponse.success(product, "Product rejected successfully"));
  }

  @DeleteMapping("/products/{id}/force")
  public ResponseEntity<ApiResponse<Void>> forceDeleteProduct(@PathVariable Long id) {

    productService.forceDeleteProduct(id);
    return ResponseEntity.ok(ApiResponse.success("Product permanently deleted"));
  }
}
