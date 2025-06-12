package com.yourcompany.agritrade.catalog.controller;

import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.dto.response.SupplySourceResponse;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public") // Base path cho API public
@RequiredArgsConstructor
public class PublicCatalogController {

  private final CategoryService categoryService;
  private final ProductService productService;

  @GetMapping("/categories")
  public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategoriesForDropdown() {
    // Gọi phương thức service
    List<CategoryResponse> categories = categoryService.getAllCategoriesForDropdown();
    System.out.println(
        "Handling /api/public/categories request. Found categories: "
            + (categories != null ? categories.size() : 0));
    return ResponseEntity.ok(ApiResponse.success(categories));
  }

  // --- Category Endpoints ---
  @GetMapping("/categories/tree")
  public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryTree() {
    List<CategoryResponse> categoryTree = categoryService.getCategoryTree();
    return ResponseEntity.ok(ApiResponse.success(categoryTree));
  }

  @GetMapping("/categories/{slug}")
  public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(
      @PathVariable String slug) {
    CategoryResponse category = categoryService.getCategoryBySlug(slug);
    return ResponseEntity.ok(ApiResponse.success(category));
  }

  // --- Product Endpoints ---
  @GetMapping("/products")
  public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> searchProducts(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer categoryId,
      @RequestParam(required = false) String provinceCode,
      @RequestParam(required = false) Double minPrice,
      @RequestParam(required = false) Double maxPrice,
      @RequestParam(required = false) Integer minRating,
      @PageableDefault(size = 12, sort = "created_at,desc") Pageable pageable) {
    Page<ProductSummaryResponse> products =
        productService.searchPublicProducts(
            keyword, categoryId, provinceCode, minPrice, maxPrice, minRating, pageable);
    return ResponseEntity.ok(ApiResponse.success(products));
  }

  @GetMapping("/products/{slug}")
  public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(
      @PathVariable String slug) {
    ProductDetailResponse product = productService.getPublicProductBySlug(slug);
    return ResponseEntity.ok(ApiResponse.success(product));
  }

  @GetMapping("/products/id/{id}") // endpoint lấy theo ID
  public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductById(@PathVariable Long id) {
    ProductDetailResponse product = productService.getPublicProductById(id);
    return ResponseEntity.ok(ApiResponse.success(product));
  }

  @GetMapping("/farmer/{farmerId}/products")
  public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getProductsByFarmerPublic(
      @PathVariable Long farmerId,
      @PageableDefault(size = 8, sort = "createdAt,desc") Pageable pageable) {
    // Gọi service
    Page<ProductSummaryResponse> products =
        productService.getPublicProductsByFarmerId(farmerId, pageable);
    return ResponseEntity.ok(ApiResponse.success(products));
  }

  @GetMapping("/supply-sources")
  public ResponseEntity<ApiResponse<Page<SupplySourceResponse>>> findSupplySources(
      @RequestParam(required = false) String productKeyword,
      @RequestParam(required = false) Integer categoryId,
      @RequestParam(required = false) String provinceCode,
      @RequestParam(required = false) String districtCode,
      @RequestParam(required = false) String wardCode,
      @RequestParam(required = false) Integer minQuantityNeeded,
      @PageableDefault(size = 10, sort = "farmerInfo.farmName,asc") Pageable pageable) {

    // Gọi service
    Page<SupplySourceResponse> supplySources =
        productService.findSupplySources(
            productKeyword,
            categoryId,
            provinceCode,
            districtCode,
            wardCode,
            minQuantityNeeded,
            pageable);
    return ResponseEntity.ok(ApiResponse.success(supplySources));
  }
}
