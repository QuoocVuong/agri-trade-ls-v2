package com.yourcompany.agritradels.catalog.controller;

import com.yourcompany.agritradels.catalog.dto.response.CategoryResponse;
import com.yourcompany.agritradels.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritradels.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritradels.catalog.service.CategoryService;
import com.yourcompany.agritradels.catalog.service.ProductService;
import com.yourcompany.agritradels.common.dto.ApiResponse;
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
            @PageableDefault(size = 12, sort = "created_at,desc") Pageable pageable) {
        Page<ProductSummaryResponse> products = productService.searchPublicProducts(keyword, categoryId, provinceCode, pageable);
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

}