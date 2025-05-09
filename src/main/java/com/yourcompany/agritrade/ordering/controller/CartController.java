package com.yourcompany.agritrade.ordering.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest;
import com.yourcompany.agritrade.ordering.dto.request.CartItemUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartValidationResponse;
import com.yourcompany.agritrade.ordering.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho tất cả API giỏ hàng
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getMyCart(Authentication authentication) {
        CartResponse cart = cartService.getCart(authentication);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartItemResponse>> addItemToCart(
            Authentication authentication,
            @Valid @RequestBody CartItemRequest request) {
        CartItemResponse addedItem = cartService.addItem(authentication, request);
        // Trả về 201 Created hoặc 200 OK tùy theo ngữ cảnh (thêm mới hay cập nhật)
        // Ở đây dùng 200 OK cho đơn giản vì nó xử lý cả 2 trường hợp
        return ResponseEntity.ok(ApiResponse.success(addedItem, "Item added/updated in cart"));
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemResponse>> updateCartItemQuantity(
            Authentication authentication,
            @PathVariable Long cartItemId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        CartItemResponse updatedItem = cartService.updateItemQuantity(authentication, cartItemId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedItem, "Cart item quantity updated"));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> removeCartItem(
            Authentication authentication,
            @PathVariable Long cartItemId) {
        cartService.removeItem(authentication, cartItemId);
        // Trả về 200 OK hoặc 204 No Content
        return ResponseEntity.ok(ApiResponse.success("Cart item removed successfully"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearMyCart(Authentication authentication) {
        cartService.clearCart(authentication);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared successfully"));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<CartValidationResponse>> validateCart(Authentication authentication) {
        CartValidationResponse validationResponse = cartService.validateCartForCheckout(authentication);
        return ResponseEntity.ok(ApiResponse.success(validationResponse));
    }
}