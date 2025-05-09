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
import com.yourcompany.agritrade.ordering.dto.response.CartAdjustmentInfo;
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.mapper.CartItemMapper;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.ordering.service.CartService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
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
    @Transactional
    public CartResponse getCart(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        List<CartItem> cartItemsFromDb = cartItemRepository.findByUserId(user.getId());
        List<CartItemResponse> validItemResponses = new ArrayList<>();
        List<Long> itemsToRemoveFromDb = new ArrayList<>(); // Lưu ID của CartItem cần xóa
        List<CartItem> itemsToUpdateInDb = new ArrayList<>(); // Lưu CartItem cần cập nhật số lượng
        List<CartAdjustmentInfo> adjustments = new ArrayList<>(); // Lưu thông tin điều chỉnh


        for (Iterator<CartItem> iterator = cartItemsFromDb.iterator(); iterator.hasNext();) {
            CartItem cartItem = iterator.next();
            Product product = null;
            boolean removeThisCartItem = false;

            if (cartItem.getProduct() == null) {
                // Trường hợp 1: Product đã là null khi CartItem được load (do @Where trên Product)
                log.warn("CartItem ID {} for user {} references a product that is null (likely soft-deleted or non-existent). Marking for removal.", cartItem.getId(), user.getId());
                removeThisCartItem = true;
            } else {
                try {
                    // Trường hợp 2: Cố gắng load lại product từ DB để chắc chắn
                    // findById sẽ trả về empty nếu product bị xóa mềm (do @Where) hoặc không tồn tại
                    Optional<Product> productOpt = productRepository.findById(cartItem.getProduct().getId());
                    if (productOpt.isEmpty()) {
                        log.warn("Product ID {} for CartItem ID {} (user {}) not found or soft-deleted. Marking for removal.", cartItem.getProduct().getId(), cartItem.getId(), user.getId());
                        removeThisCartItem = true;
                    } else {
                        product = productOpt.get();
                        // Kiểm tra thêm trạng thái PUBLISHED
                        if (product.getStatus() != ProductStatus.PUBLISHED) {
                            log.warn("Product ID {} for CartItem ID {} (user {}) is not PUBLISHED (status: {}). Marking for removal.", product.getId(), cartItem.getId(), user.getId(), product.getStatus());
                            removeThisCartItem = true;
                        }
                    }
                } catch (EntityNotFoundException e) {
                    // Bắt lỗi nếu có vấn đề khi truy cập product (dù ít xảy ra nếu @Where hoạt động)
                    log.warn("EntityNotFoundException for product in CartItem ID {} (user {}). Marking for removal. Error: {}", cartItem.getId(), user.getId(), e.getMessage());
                    removeThisCartItem = true;
                }
            }

            if (removeThisCartItem) {
                itemsToRemoveFromDb.add(cartItem.getId()); // Thêm vào danh sách cần xóa
            } else if (product != null) { // Chỉ map nếu product hợp lệ
                // Gán lại product đã load (nếu có) vào cartItem để mapper sử dụng đúng đối tượng
                cartItem.setProduct(product);
                validItemResponses.add(cartItemMapper.toCartItemResponse(cartItem));
            }
        }

        // Xóa các CartItem không hợp lệ khỏi CSDL
        if (!itemsToRemoveFromDb.isEmpty()) {
            log.info("Removing {} invalid cart items for user {}: {}", itemsToRemoveFromDb.size(), user.getId(), itemsToRemoveFromDb);
            cartItemRepository.deleteAllByIdInBatch(itemsToRemoveFromDb); // Xóa theo batch hiệu quả hơn
        }


        // Tính toán dựa trên validItemResponses
        BigDecimal subTotal = validItemResponses.stream()
                .map(CartItemResponse::getItemTotal)
                .filter(java.util.Objects::nonNull) // Bỏ qua item không tính được total
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = validItemResponses.stream().mapToInt(CartItemResponse::getQuantity).sum();

        if (!itemsToRemoveFromDb.isEmpty()) {
            log.warn("User {}'s cart had {} items removed because their products were no longer available.", user.getId(), itemsToRemoveFromDb.size());
            // Có thể thêm thông báo vào CartResponse nếu muốn báo cho user
        }


        return new CartResponse(validItemResponses, subTotal, totalItems);
    }

    @Override
    @Transactional
    public CartItemResponse addItem(Authentication authentication, CartItemRequest request) {
        User user = getUserFromAuthentication(authentication);
        Product product;
        try {
            // Gọi findAvailableProduct để kiểm tra tồn tại, status và is_deleted (nhờ @Where)
            product = findAvailableProduct(request.getProductId());
        } catch (ResourceNotFoundException e) {
            // Nếu findById không tìm thấy (do không tồn tại hoặc is_deleted=true)
            log.warn("Attempt to add non-existent or deleted product (ID: {}) to cart by user {}", request.getProductId(), user.getId());
            throw new BadRequestException("Sản phẩm bạn chọn không tồn tại hoặc đã ngừng bán."); // Thông báo rõ ràng hơn
        } catch (BadRequestException e) { // Bắt lỗi nếu sản phẩm không PUBLISHED
            throw e; // Ném lại lỗi gốc
        }

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

        Product product;
        try {
            // Kiểm tra lại sản phẩm khi cập nhật số lượng
            product = findAvailableProduct(cartItem.getProduct().getId());
        } catch (ResourceNotFoundException e) {
            log.warn("Product (ID: {}) in cart item {} no longer exists or is deleted. Removing item.", cartItem.getProduct().getId(), cartItemId);
            // Tự động xóa item khỏi giỏ nếu sản phẩm không còn hợp lệ
            cartItemRepository.delete(cartItem);
            // Ném lỗi khác để báo cho frontend biết item đã bị xóa
            throw new BadRequestException("Sản phẩm trong giỏ hàng không còn tồn tại và đã được xóa.");
        } catch (BadRequestException e) { // Bắt lỗi nếu sản phẩm không PUBLISHED
            // Tương tự, xóa item khỏi giỏ
            cartItemRepository.delete(cartItem);
            throw new BadRequestException("Sản phẩm trong giỏ hàng không còn được bán và đã được xóa.");
        }
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
        // Thêm kiểm tra isDeleted (dù @Where đã làm) để chắc chắn
         if (product.isDeleted()) {
             log.warn("Attempted to access a soft-deleted product with id: {}", productId);
             throw new ResourceNotFoundException("Product", "id", productId);
         }
        if(product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BadRequestException("Product is not available: " + product.getName());
        }
        return product;
    }
}