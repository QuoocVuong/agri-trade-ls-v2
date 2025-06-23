package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.ordering.domain.CartItem;
import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest;
import com.yourcompany.agritrade.ordering.dto.request.CartItemUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.CartAdjustmentInfo;
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartValidationResponse;
import com.yourcompany.agritrade.ordering.mapper.CartItemMapper;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.ordering.service.CartService;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    List<CartItem> cartItemsFromDb = cartItemRepository.findByUserId(user.getId());
    List<CartItemResponse> validItemResponses = new ArrayList<>();
    List<Long> itemsToRemoveFromDb = new ArrayList<>(); // Lưu ID của CartItem cần xóa
    List<CartItem> itemsToUpdateInDb = new ArrayList<>(); // Lưu CartItem cần cập nhật số lượng
    List<CartAdjustmentInfo> adjustments = new ArrayList<>(); // Lưu thông tin điều chỉnh

    for (CartItem cartItem : cartItemsFromDb) { // Không dùng iterator nữa để có thể modify list
      Product product = null;
      boolean markForRemoval = false;
      String adjustmentMessage = null;
      String adjustmentType = null;
      String productNameForMessage = "Sản phẩm không xác định"; // Tên mặc định

      if (cartItem.getProduct() != null) {
        productNameForMessage =
            cartItem.getProduct().getName(); // Lấy tên trước khi product có thể bị null
      }

      if (cartItem.getProduct() == null) {
        log.warn(
            "CartItem ID {} for user {} references a product that is null. Marking for removal.",
            cartItem.getId(),
            user.getId());
        markForRemoval = true;
        adjustmentMessage =
            "Một sản phẩm ("
                + productNameForMessage
                + ") không còn tồn tại và đã được xóa khỏi giỏ.";
        adjustmentType = "REMOVED";
      } else {
        Optional<Product> productOpt = productRepository.findById(cartItem.getProduct().getId());
        if (productOpt.isEmpty()) {
          log.warn(
              "Product ID {} for CartItem ID {} (user {}) not found or soft-deleted. Marking for removal.",
              cartItem.getProduct().getId(),
              cartItem.getId(),
              user.getId());
          markForRemoval = true;
          adjustmentMessage =
              "Sản phẩm '" + productNameForMessage + "' không còn tồn tại và đã được xóa khỏi giỏ.";
          adjustmentType = "REMOVED";
        } else {
          product = productOpt.get();
          if (product.getStatus() != ProductStatus.PUBLISHED || product.isDeleted()) {
            log.warn(
                "GET_CART: Product ID {} (CartItem ID {}) is not PUBLISHED or is deleted. Marking for removal.",
                product.getId(),
                cartItem.getId());
            markForRemoval = true;
            adjustmentMessage =
                "Sản phẩm '" + product.getName() + "' không còn được bán và đã được xóa khỏi giỏ.";
            adjustmentType = "REMOVED";
          } else {
            //  KIỂM TRA VÀ ĐIỀU CHỈNH SỐ LƯỢNG
            int currentStock = product.getStockQuantity();
            int quantityInCart = cartItem.getQuantity();

            if (currentStock <= 0) { // Hết hàng
              log.warn(
                  "Product ID {} (CartItem ID {}) is out of stock. Marking for removal.",
                  product.getId(),
                  cartItem.getId());
              markForRemoval = true;
              adjustmentMessage =
                  "Sản phẩm '" + product.getName() + "' đã hết hàng và được xóa khỏi giỏ.";
              adjustmentType = "REMOVED";
            } else if (quantityInCart > currentStock) { // Số lượng trong giỏ > tồn kho
              log.warn(
                  "Quantity for Product ID {} (CartItem ID {}) in cart ({}) exceeds stock ({}). Adjusting quantity.",
                  product.getId(),
                  cartItem.getId(),
                  quantityInCart,
                  currentStock);
              cartItem.setQuantity(currentStock); // Điều chỉnh số lượng
              itemsToUpdateInDb.add(cartItem); // Thêm vào danh sách cần cập nhật DB
              adjustmentMessage =
                  "Số lượng sản phẩm '"
                      + product.getName()
                      + "' đã được cập nhật thành "
                      + currentStock
                      + " do thay đổi tồn kho.";
              adjustmentType = "ADJUSTED";
            }
          }
        }
      }

      if (markForRemoval) {
        itemsToRemoveFromDb.add(cartItem.getId());
        if (adjustmentMessage != null) {
          adjustments.add(
              new CartAdjustmentInfo(
                  cartItem.getProduct() != null ? cartItem.getProduct().getId() : null,
                  cartItem.getProduct() != null
                      ? cartItem.getProduct().getName()
                      : "Sản phẩm không xác định",
                  adjustmentMessage,
                  "REMOVED"));
        }
      } else if (product != null) {
        cartItem.setProduct(product); // Đảm bảo product được gán lại
        validItemResponses.add(cartItemMapper.toCartItemResponse(cartItem));
        if (adjustmentMessage != null) {
          adjustments.add(
              new CartAdjustmentInfo(
                  product.getId(), product.getName(), adjustmentMessage, adjustmentType));
        }
      }
    }
    // Thực hiện cập nhật và xóa DB
    if (!itemsToUpdateInDb.isEmpty()) {
      log.info(
          "Updating quantities for {} cart items for user {}.",
          itemsToUpdateInDb.size(),
          user.getId());
      cartItemRepository.saveAll(itemsToUpdateInDb); // Lưu các thay đổi số lượng
    }
    // Xóa các CartItem không hợp lệ khỏi CSDL
    if (!itemsToRemoveFromDb.isEmpty()) {
      log.info(
          "Removing {} invalid/out-of-stock cart items for user {}: {}",
          itemsToRemoveFromDb.size(),
          user.getId(),
          itemsToRemoveFromDb);
      cartItemRepository.deleteAllByIdInBatch(itemsToRemoveFromDb);
    }

    // Tính toán dựa trên validItemResponses
    BigDecimal subTotal =
        validItemResponses.stream()
            .map(CartItemResponse::getItemTotal)
            .filter(java.util.Objects::nonNull) // Bỏ qua item không tính được total
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    int totalItems = validItemResponses.stream().mapToInt(CartItemResponse::getQuantity).sum();

    CartResponse cartResponse =
        new CartResponse(validItemResponses, subTotal, totalItems, adjustments);
    if (!adjustments.isEmpty()) {
      cartResponse.setAdjustments(adjustments); // Thêm thông tin điều chỉnh vào response
    }
    return cartResponse;
  }

  @Override
  @Transactional
  public CartItemResponse addItem(Authentication authentication, CartItemRequest request) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    Product product;
    try {
      // Gọi findAvailableProduct để kiểm tra tồn tại, status và is_deleted (nhờ @Where)
      product = findAvailableProduct(request.getProductId());
    } catch (ResourceNotFoundException e) {
      // Nếu findById không tìm thấy (do không tồn tại hoặc is_deleted=true)
      log.warn(
          "Attempt to add non-existent or deleted product (ID: {}) to cart by user {}",
          request.getProductId(),
          user.getId());
      throw new BadRequestException(
          "Sản phẩm bạn chọn không tồn tại hoặc đã ngừng bán."); // Thông báo rõ ràng hơn
    } catch (BadRequestException e) { // Bắt lỗi nếu sản phẩm không PUBLISHED
      throw e; // Ném lại lỗi gốc
    }

    int currentStock = product.getStockQuantity(); // Lấy tồn kho hiện tại

    // Kiểm tra số lượng yêu cầu
    if (currentStock < request.getQuantity()) {

      throw new OutOfStockException(
          "Not enough stock for product: " + product.getName(),
          currentStock // Truyền số lượng tồn thực tế
          );
    }

    Optional<CartItem> existingItemOpt =
        cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId());
    CartItem cartItem;
    if (existingItemOpt.isPresent()) {
      // Cập nhật số lượng
      cartItem = existingItemOpt.get();
      int newTotalQuantity =
          cartItem.getQuantity() + request.getQuantity(); // Tính tổng số lượng sẽ có trong giỏ
      // Kiểm tra lại tồn kho với tổng số lượng mới
      if (currentStock < newTotalQuantity) {

        throw new OutOfStockException(
            "Not enough stock for product: "
                + product.getName()
                + " (requested total: "
                + newTotalQuantity
                + ")",
            currentStock // Vẫn là số lượng tồn thực tế
            );
      }
      cartItem.setQuantity(newTotalQuantity);
      log.info(
          "Updated quantity for product {} in cart for user {}", product.getId(), user.getId());
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
  public CartItemResponse updateItemQuantity(
      Authentication authentication, Long cartItemId, CartItemUpdateRequest request) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    CartItem cartItem =
        cartItemRepository
            .findById(cartItemId)
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
      log.warn(
          "Product (ID: {}) in cart item {} no longer exists or is deleted. Removing item.",
          cartItem.getProduct().getId(),
          cartItemId);
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

      throw new OutOfStockException(
          "Not enough stock for product: " + product.getName(),
          currentStock // Truyền số lượng tồn thực tế
          );
    }

    cartItem.setQuantity(request.getQuantity());
    CartItem updatedItem = cartItemRepository.save(cartItem);
    log.info("Updated quantity for cart item {} for user {}", cartItemId, user.getId());
    return cartItemMapper.toCartItemResponse(updatedItem);
  }

  @Override
  @Transactional
  public void removeItem(Authentication authentication, Long cartItemId) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    CartItem cartItem =
        cartItemRepository
            .findById(cartItemId)
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
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    cartItemRepository.deleteByUserId(user.getId());
    log.info("Cleared cart for user {}", user.getId());
  }

  private Product findAvailableProduct(Long productId) {
    Product product =
        productRepository
            .findById(productId) // findById đã lọc is_deleted=false
            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
    // Thêm kiểm tra isDeleted  để chắc chắn
    if (product.isDeleted()) {
      log.warn("Attempted to access a soft-deleted product with id: {}", productId);
      throw new ResourceNotFoundException("Product", "id", productId);
    }
    if (product.getStatus() != ProductStatus.PUBLISHED) {
      throw new BadRequestException("Product is not available: " + product.getName());
    }
    return product;
  }

  @Override
  @Transactional
  public CartValidationResponse validateCartForCheckout(Authentication authentication) {
    User user = SecurityUtils.getCurrentAuthenticatedUser();
    List<CartItem> cartItemsFromDb = cartItemRepository.findByUserId(user.getId());

    if (cartItemsFromDb.isEmpty()) {
      return new CartValidationResponse(
          true,
          Collections.singletonList("Giỏ hàng của bạn đang trống. Không thể thanh toán."),
          Collections.emptyList());
    }

    boolean isCartGloballyValidForCheckout = true; // Cờ này sẽ là false nếu có BẤT KỲ thay đổi nào
    List<CartAdjustmentInfo> adjustments = new ArrayList<>();
    List<Long> itemsToRemoveFromDbById = new ArrayList<>();
    List<CartItem> itemsToUpdateInDb = new ArrayList<>();

    for (CartItem cartItem : cartItemsFromDb) {
      Product product = null;
      boolean currentItemInvalidated = false;
      String adjustmentMessage = null;
      String adjustmentType = null;
      String productNameForMessage = "Sản phẩm không xác định";

      if (cartItem.getProduct() != null) {
        productNameForMessage = cartItem.getProduct().getName();
      }

      if (cartItem.getProduct() == null) {
        log.warn(
            "VALIDATE_CART: CartItem ID {} for user {} references a product that is null. Marking for removal.",
            cartItem.getId(),
            user.getId());
        itemsToRemoveFromDbById.add(cartItem.getId());
        adjustmentMessage =
            "Một sản phẩm ("
                + productNameForMessage
                + ") không còn tồn tại và sẽ được xóa khỏi giỏ.";
        adjustmentType = "REMOVED";
        currentItemInvalidated = true;
      } else {
        Optional<Product> productOpt = productRepository.findById(cartItem.getProduct().getId());
        if (productOpt.isEmpty()) {
          log.warn(
              "VALIDATE_CART: Product ID {} for CartItem ID {} (user {}) not found or soft-deleted. Marking for removal.",
              cartItem.getProduct().getId(),
              cartItem.getId(),
              user.getId());
          itemsToRemoveFromDbById.add(cartItem.getId());
          adjustmentMessage =
              "Sản phẩm '" + productNameForMessage + "' không còn tồn tại và sẽ được xóa khỏi giỏ.";
          adjustmentType = "REMOVED";
          currentItemInvalidated = true;
        } else {
          product = productOpt.get();
          if (product.getStatus() != ProductStatus.PUBLISHED || product.isDeleted()) {
            log.warn(
                "VALIDATE_CART: Product ID {} (CartItem ID {}) is not PUBLISHED or is deleted. Marking for removal.",
                product.getId(),
                cartItem.getId());
            itemsToRemoveFromDbById.add(cartItem.getId());
            adjustmentMessage =
                "Sản phẩm '" + product.getName() + "' không còn được bán và sẽ được xóa khỏi giỏ.";
            adjustmentType = "REMOVED";
            currentItemInvalidated = true;
          } else {
            int currentStock = product.getStockQuantity();
            int quantityInCart = cartItem.getQuantity();
            productNameForMessage = product.getName();

            if (currentStock <= 0) {
              log.warn(
                  "VALIDATE_CART: Product ID {} (CartItem ID {}) is out of stock. Marking for removal.",
                  product.getId(),
                  cartItem.getId());
              itemsToRemoveFromDbById.add(cartItem.getId());
              adjustmentMessage =
                  "Sản phẩm '" + product.getName() + "' đã hết hàng và sẽ được xóa khỏi giỏ.";
              adjustmentType = "REMOVED";
              currentItemInvalidated = true;
            } else if (quantityInCart > currentStock) {
              log.warn(
                  "VALIDATE_CART: Quantity for Product ID {} (CartItem ID {}) in cart ({}) exceeds stock ({}). Adjusting quantity.",
                  product.getId(),
                  cartItem.getId(),
                  quantityInCart,
                  currentStock);
              cartItem.setQuantity(currentStock);
              itemsToUpdateInDb.add(cartItem);
              adjustmentMessage =
                  "Số lượng sản phẩm '"
                      + product.getName()
                      + "' đã được cập nhật thành "
                      + currentStock
                      + " do thay đổi tồn kho.";
              adjustmentType = "ADJUSTED";
              currentItemInvalidated = true; // Đánh dấu có thay đổi, cần user review
            }
          }
        }
      }

      if (currentItemInvalidated) {
        isCartGloballyValidForCheckout =
            false; // Nếu có bất kỳ item nào bị xóa hoặc điều chỉnh, giỏ hàng không còn "hoàn toàn
        // hợp lệ" để checkout ngay
        if (adjustmentMessage != null) {
          adjustments.add(
              new CartAdjustmentInfo(
                  cartItem.getProduct() != null ? cartItem.getProduct().getId() : null,
                  productNameForMessage,
                  adjustmentMessage,
                  adjustmentType));
        }
      }
    }

    // Thực hiện cập nhật và xóa DB nếu có thay đổi
    if (!itemsToUpdateInDb.isEmpty()) {
      cartItemRepository.saveAllAndFlush(itemsToUpdateInDb);
    }
    if (!itemsToRemoveFromDbById.isEmpty()) {
      cartItemRepository.deleteAllByIdInBatch(itemsToRemoveFromDbById);
    }

    List<String> messagesForUser =
        adjustments.stream().map(CartAdjustmentInfo::getMessage).collect(Collectors.toList());
    // Nếu không có điều chỉnh nào và giỏ hàng vẫn hợp lệ từ đầu
    if (messagesForUser.isEmpty() && isCartGloballyValidForCheckout) {
      messagesForUser.add("Giỏ hàng của bạn hợp lệ để tiếp tục thanh toán.");
    }

    return new CartValidationResponse(isCartGloballyValidForCheckout, messagesForUser, adjustments);
  }
}
