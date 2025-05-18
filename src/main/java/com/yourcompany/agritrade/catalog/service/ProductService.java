package com.yourcompany.agritrade.catalog.service;

import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse;
import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

// Interface cho ProductService
public interface ProductService {

    // Cho Farmer
    Page<ProductSummaryResponse> getMyProducts(Authentication authentication, Pageable pageable);
    ProductDetailResponse getMyProductById(Authentication authentication, Long productId);
    ProductDetailResponse createMyProduct(Authentication authentication, ProductRequest request);
    ProductDetailResponse updateMyProduct(Authentication authentication, Long productId, ProductRequest request);
    void deleteMyProduct(Authentication authentication, Long productId); // Soft delete

    // Cho Public/Buyers
    Page<ProductSummaryResponse> searchPublicProducts(
            String keyword,
            Integer categoryId,
            String provinceCode,
            Double minPrice,
            Double maxPrice,
            Integer minRating,
            Pageable pageable);
    ProductDetailResponse getPublicProductBySlug(String slug);
    ProductDetailResponse getPublicProductById(Long id); // Lấy theo ID nếu cần

    // ===== PHƯƠNG THỨC MỚI =====
    /**
     * Lấy danh sách sản phẩm công khai (PUBLISHED) của một Farmer cụ thể.
     * @param farmerId ID của Farmer.
     * @param pageable Thông tin phân trang và sắp xếp.
     * @return Trang chứa các ProductSummaryResponse.
     */
    Page<ProductSummaryResponse> getPublicProductsByFarmerId(Long farmerId, Pageable pageable);
    // ===========================

    // Cho Admin
    Page<ProductSummaryResponse> getAllProductsForAdmin(String status, Integer categoryId, Long farmerId, Pageable pageable);
    ProductDetailResponse getProductByIdForAdmin(Long productId); // Lấy cả sản phẩm chưa publish/đã xóa
    ProductDetailResponse approveProduct(Long productId);
    ProductDetailResponse rejectProduct(Long productId, String reason); // Có thể thêm lý do từ chối
    void forceDeleteProduct(Long productId); // Xóa vật lý (nếu cần)

}