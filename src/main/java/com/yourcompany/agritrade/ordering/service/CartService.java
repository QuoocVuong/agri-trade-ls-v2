package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest;
import com.yourcompany.agritrade.ordering.dto.request.CartItemUpdateRequest; // DTO mới cho update
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartValidationResponse;
import org.springframework.security.core.Authentication;

public interface CartService {

    /** Lấy thông tin giỏ hàng của user hiện tại */
    CartResponse getCart(Authentication authentication);

    /** Thêm sản phẩm vào giỏ hoặc cập nhật số lượng nếu đã có */
    CartItemResponse addItem(Authentication authentication, CartItemRequest request);

    /** Cập nhật số lượng của một item trong giỏ */
    CartItemResponse updateItemQuantity(Authentication authentication, Long cartItemId, CartItemUpdateRequest request);

    /** Xóa một item khỏi giỏ hàng */
    void removeItem(Authentication authentication, Long cartItemId);

    /** Xóa toàn bộ giỏ hàng của user */
    void clearCart(Authentication authentication);

    // ...
    CartValidationResponse validateCartForCheckout(Authentication authentication);
}