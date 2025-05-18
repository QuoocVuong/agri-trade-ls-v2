package com.yourcompany.agritrade.catalog.controller;

import com.yourcompany.agritrade.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.service.CategoryService;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public") // Base path cho API public
@RequiredArgsConstructor
public class PublicCatalogController {

    private final CategoryService categoryService;
    private final ProductService productService;


    @GetMapping("/categories") // Map với URL mà frontend đang gọi
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategoriesForDropdown() {
        // Gọi phương thức service mới đã tạo
        List<CategoryResponse> categories = categoryService.getAllCategoriesForDropdown();
        System.out.println("Handling /api/public/categories request. Found categories: " + (categories != null ? categories.size() : 0));
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    // --- Category Endpoints ---
    @GetMapping("/categories/tree")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryTree() {
        List<CategoryResponse> categoryTree = categoryService.getCategoryTree();
        return ResponseEntity.ok(ApiResponse.success(categoryTree));
    }

    @GetMapping("/categories/{slug}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(@PathVariable String slug) {
        CategoryResponse category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    // --- Product Endpoints ---
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String provinceCode, // Lọc theo tỉnh (cho B2C)

            @RequestParam(required = false) Double minPrice,   // Thêm
            @RequestParam(required = false) Double maxPrice,   // Thêm
            @RequestParam(required = false) Integer minRating, // Thêm (dùng Integer để có thể là null)

            @PageableDefault(size = 12, sort = "created_at,desc") Pageable pageable) {
        Page<ProductSummaryResponse> products = productService.searchPublicProducts(keyword, categoryId, provinceCode, minPrice, maxPrice, minRating, pageable);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/products/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(@PathVariable String slug) {
        ProductDetailResponse product = productService.getPublicProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }
    @GetMapping("/products/id/{id}") // Thêm endpoint lấy theo ID nếu cần
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductById(@PathVariable Long id) {
        ProductDetailResponse product = productService.getPublicProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    // Trong PublicCatalogController.java
    @GetMapping("/farmer/{farmerId}/products")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getProductsByFarmerPublic(
            @PathVariable Long farmerId,
            @PageableDefault(size = 8, sort = "createdAt,desc") Pageable pageable) {
        // Gọi service để lấy sản phẩm public của farmer này
        Page<ProductSummaryResponse> products = productService.getPublicProductsByFarmerId(farmerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

}