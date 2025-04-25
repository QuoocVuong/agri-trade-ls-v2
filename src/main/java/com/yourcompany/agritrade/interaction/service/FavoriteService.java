package com.yourcompany.agritrade.interaction.service;

import com.yourcompany.agritrade.catalog.dto.response.ProductSummaryResponse; // Import Product Summary
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface FavoriteService {

    /** Thêm sản phẩm vào danh sách yêu thích của user hiện tại */
    void addFavorite(Authentication authentication, Long productId);

    /** Xóa sản phẩm khỏi danh sách yêu thích của user hiện tại */
    void removeFavorite(Authentication authentication, Long productId);

    /** Lấy danh sách sản phẩm yêu thích của user hiện tại (phân trang) */
    Page<ProductSummaryResponse> getMyFavorites(Authentication authentication, Pageable pageable);

    /** Kiểm tra xem user hiện tại có yêu thích sản phẩm này không */
    boolean isFavorite(Authentication authentication, Long productId);
}