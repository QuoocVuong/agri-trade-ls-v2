package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.ordering.domain.CartItem;
import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest;
import com.yourcompany.agritrade.ordering.dto.request.CartItemUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.mapper.CartItemMapper;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.ordering.service.CartService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemMapper cartItemMapper;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        List<CartItem> items = cartItemRepository.findByUserId(user.getId());
        List<CartItemResponse> itemResponses = cartItemMapper.toCartItemResponseList(items);

        BigDecimal subTotal = itemResponses.stream()
                .map(CartItemResponse::getItemTotal)
                .filter(java.util.Objects::nonNull) // Bỏ qua item không tính được total
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = itemResponses.stream().mapToInt(CartItemResponse::getQuantity).sum();

        return new CartResponse(itemResponses, subTotal, totalItems);
    }

    @Override
    @Transactional
    public CartItemResponse addItem(Authentication authentication, CartItemRequest request) {
        User user = getUserFromAuthentication(authentication);
        Product product = findAvailableProduct(request.getProductId()); // Helper kiểm tra tồn tại và status

        int currentStock = product.getStockQuantity(); // Lấy tồn kho hiện tại

        // Kiểm tra số lượng yêu cầu (chỉ cho lần thêm này)
        if (currentStock < request.getQuantity()) {
            // ****** SỬA DÒNG NÀY ******
            throw new OutOfStockException(
                    "Not enough stock for product: " + product.getName(),
                    currentStock // Truyền số lượng tồn thực tế
            );
            // *************************
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId());
        CartItem cartItem;
        if (existingItemOpt.isPresent()) {
            // Cập nhật số lượng
            cartItem = existingItemOpt.get();
            int newTotalQuantity = cartItem.getQuantity() + request.getQuantity(); // Tính tổng số lượng sẽ có trong giỏ
            // Kiểm tra lại tồn kho với tổng số lượng mới
            if (currentStock < newTotalQuantity) {
                // ****** SỬA DÒNG NÀY ******
                throw new OutOfStockException(
                        "Not enough stock for product: " + product.getName() + " (requested total: " + newTotalQuantity + ")",
                        currentStock // Vẫn là số lượng tồn thực tế
                );
                // *************************
            }
            cartItem.setQuantity(newTotalQuantity);
            log.info("Updated quantity for product {} in cart for user {}", product.getId(), user.getId());
        } else {
            // Tạo mới
            cartItem = new CartItem();
            cartItem.setUser(user);
            cartItem.setProduct(product);
            cartItem.setQuantity(request.getQuantity());
            log.info("Added product {} to cart for user {}", product.getId(), user.getId());
        }
        CartItem savedItem = cartItemRepository.save(cartItem);
        return cartItemMapper.toCartItemResponse(savedItem);
    }

    @Override
    @Transactional
    public CartItemResponse updateItemQuantity(Authentication authentication, Long cartItemId, CartItemUpdateRequest request) {
        User user = getUserFromAuthentication(authentication);
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart Item", "id", cartItemId));

        // Kiểm tra ownership
        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not own this cart item");
        }

        Product product = findAvailableProduct(cartItem.getProduct().getId()); // Kiểm tra lại sản phẩm
        int currentStock = product.getStockQuantity(); // Lấy tồn kho hiện tại
        int newQuantity = request.getQuantity(); // Số lượng mới yêu cầu

        // Kiểm tra tồn kho với số lượng mới
        if (currentStock < newQuantity) {
            // ****** SỬA DÒNG NÀY ******
            throw new OutOfStockException(
                    "Not enough stock for product: " + product.getName(),
                    currentStock // Truyền số lượng tồn thực tế

            );
            // *************************
        }

        cartItem.setQuantity(request.getQuantity());
        CartItem updatedItem = cartItemRepository.save(cartItem);
        log.info("Updated quantity for cart item {} for user {}", cartItemId, user.getId());
        return cartItemMapper.toCartItemResponse(updatedItem);
    }

    @Override
    @Transactional
    public void removeItem(Authentication authentication, Long cartItemId) {
        User user = getUserFromAuthentication(authentication);
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart Item", "id", cartItemId));

        // Kiểm tra ownership
        if (!cartItem.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("User does not own this cart item");
        }

        cartItemRepository.delete(cartItem);
        log.info("Removed cart item {} for user {}", cartItemId, user.getId());
    }

    @Override
    @Transactional
    public void clearCart(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        cartItemRepository.deleteByUserId(user.getId());
        log.info("Cleared cart for user {}", user.getId());
    }

    // --- Helper Methods ---
    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email) // findByEmail đã lọc is_deleted=false
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    private Product findAvailableProduct(Long productId) {
        Product product = productRepository.findById(productId) // findById đã lọc is_deleted=false
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        if(product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BadRequestException("Product is not available: " + product.getName());
        }
        return product;
    }
}