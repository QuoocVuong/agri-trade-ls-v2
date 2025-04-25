package com.yourcompany.agritrade.interaction.controller;

import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse;
import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.interaction.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho các thao tác yêu thích
public class FavoriteController {

    private final FavoriteService favoriteService;

    // Lấy danh sách sản phẩm yêu thích của tôi
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getMyFavorites(
            Authentication authentication,
            @PageableDefault(size = 12, sort = "addedAt,desc") Pageable pageable) {
        Page<ProductSummaryResponse> favorites = favoriteService.getMyFavorites(authentication, pageable);
        return ResponseEntity.ok(ApiResponse.success(favorites));
    }

    // Thêm sản phẩm vào yêu thích
    @PostMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Void>> addFavorite(Authentication authentication, @PathVariable Long productId) {
        favoriteService.addFavorite(authentication, productId);
        return ResponseEntity.ok(ApiResponse.success("Product added to favorites"));
    }

    // Xóa sản phẩm khỏi yêu thích
    @DeleteMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(Authentication authentication, @PathVariable Long productId) {
        favoriteService.removeFavorite(authentication, productId);
        return ResponseEntity.ok(ApiResponse.success("Product removed from favorites"));
    }

    // Kiểm tra xem sản phẩm có trong danh sách yêu thích không
    @GetMapping("/product/{productId}/status")
    public ResponseEntity<ApiResponse<Boolean>> checkFavoriteStatus(
            Authentication authentication, @PathVariable Long productId) {
        boolean isFavorite = favoriteService.isFavorite(authentication, productId);
        return ResponseEntity.ok(ApiResponse.success(isFavorite));
    }
}