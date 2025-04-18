package com.yourcompany.agritradels.catalog.controller;

import com.yourcompany.agritradels.catalog.dto.request.ProductRequest;
import com.yourcompany.agritradels.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritradels.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritradels.catalog.service.ProductService;
import com.yourcompany.agritradels.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farmer/products") // Base path cho Farmer
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARMER')") // Yêu cầu vai trò Farmer
public class FarmerProductController {

    private final ProductService productService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getMyProducts(
            Authentication authentication,
            @PageableDefault(size = 10, sort = "updatedAt,desc") Pageable pageable) {
        Page<ProductSummaryResponse> products = productService.getMyProducts(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getMyProductById(
            Authentication authentication, @PathVariable Long id) {
        ProductDetailResponse product = productService.getMyProductById(authentication, id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PostMapping("/me")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createMyProduct(
            Authentication authentication, @Valid @RequestBody ProductRequest request) {
        ProductDetailResponse createdProduct = productService.createMyProduct(authentication, request);
        // Trả về 201 Created
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(createdProduct, "Product created successfully"));
    }

    @PutMapping("/me/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateMyProduct(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        ProductDetailResponse updatedProduct = productService.updateMyProduct(authentication, id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedProduct, "Product updated successfully"));
    }

    @DeleteMapping("/me/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMyProduct(
            Authentication authentication, @PathVariable Long id) {
        productService.deleteMyProduct(authentication, id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully")); // Hoặc 204 No Content
    }
}