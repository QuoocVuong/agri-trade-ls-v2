package com.yourcompany.agritrade.catalog.controller;

import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.catalog.dto.response.SupplySourceResponse;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.file.attribute.UserPrincipal;

@RestController
@RequestMapping("/api/farmer/products") // Base path cho Farmer
@RequiredArgsConstructor
@PreAuthorize("hasRole('FARMER')") // Yêu cầu vai trò Farmer
public class FarmerProductController {

  private final ProductService productService;

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getMyProducts(
      Authentication authentication,
      @RequestParam(required = false) String keyword, // NHẬN KEYWORD
      @RequestParam(required = false) String status, // NHẬN STATUS DẠNG STRING
      @PageableDefault(size = 10, sort = "updatedAt,desc") Pageable pageable) {

    ProductStatus statusEnum = null;
    if (StringUtils.hasText(status)) {
      try {
        statusEnum = ProductStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {

      }
    }
    Page<ProductSummaryResponse> products =
        productService.getMyProducts(authentication, keyword, statusEnum, pageable);
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
      Authentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody ProductRequest request) {
    ProductDetailResponse updatedProduct =
        productService.updateMyProduct(authentication, id, request);
    return ResponseEntity.ok(ApiResponse.success(updatedProduct, "Product updated successfully"));
  }

  @DeleteMapping("/me/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteMyProduct(
      Authentication authentication, @PathVariable Long id) {
    productService.deleteMyProduct(authentication, id);
    return ResponseEntity.ok(
        ApiResponse.success("Product deleted successfully"));
  }

//  @PostMapping("/supply-sources")
//  @PreAuthorize("hasRole('FARMER')")
//  public ApiResponse<SupplySourceResponse> registerSupplySource(
//          @Valid @RequestBody SupplySourceRequest request,
//          @AuthenticationPrincipal UserPrincipal user) {
//    if (!supplySourceService.isFarmerProfileApproved(user.getId())) {
//      throw new BadRequestException("Hồ sơ Farmer chưa được duyệt");
//    }
//    SupplySourceResponse response = supplySourceService.register(request, user.getId());
//
//    return ApiResponse.success(response);
//  }

}
