package com.yourcompany.agritrade.ordering.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import com.yourcompany.agritrade.ordering.dto.response.CartItemResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartResponse;
import com.yourcompany.agritrade.ordering.dto.response.CartValidationResponse;
import com.yourcompany.agritrade.ordering.mapper.CartItemMapper;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

  @Mock private CartItemRepository cartItemRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;
  @Mock private CartItemMapper cartItemMapper;
  @Mock private Authentication authentication;

  // SỬA LỖI: Thêm MockedStatic để quản lý mock cho lớp tiện ích SecurityUtils
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private CartServiceImpl cartService;

  private User testUser;
  private Product product1, product2, productUnavailable, productOutOfStock, productDeleted;
  private CartItem cartItemEntity1,
      cartItemEntity2,
      cartItemUnavailableEntity,
      cartItemOutOfStockEntity,
      cartItemDeletedProductEntity;
  private CartItemResponse cartItemResponse1, cartItemResponse2;

  @BeforeEach
  void setUp() {
    // SỬA LỖI: Khởi tạo mock static cho SecurityUtils trước mỗi test
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    testUser = new User();
    testUser.setId(1L);
    testUser.setEmail("cartuser@example.com");

    // SỬA LỖI: Định nghĩa hành vi mặc định cho SecurityUtils trong setUp
    // vì tất cả các phương thức trong service đều gọi nó.
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(testUser);

    product1 = new Product();
    product1.setId(10L);
    product1.setName("Sản phẩm 1");
    product1.setPrice(new BigDecimal("100.00"));
    product1.setStockQuantity(10);
    product1.setStatus(ProductStatus.PUBLISHED);
    product1.setDeleted(false);

    product2 = new Product();
    product2.setId(20L);
    product2.setName("Sản phẩm 2");
    product2.setPrice(new BigDecimal("200.00"));
    product2.setStockQuantity(5);
    product2.setStatus(ProductStatus.PUBLISHED);
    product2.setDeleted(false);

    productUnavailable = new Product();
    productUnavailable.setId(30L);
    productUnavailable.setName("Sản phẩm đã ẩn");
    productUnavailable.setPrice(new BigDecimal("50.00"));
    productUnavailable.setStatus(ProductStatus.UNPUBLISHED);
    productUnavailable.setStockQuantity(10);
    productUnavailable.setDeleted(false);

    productOutOfStock = new Product();
    productOutOfStock.setId(40L);
    productOutOfStock.setName("Sản phẩm hết hàng");
    productOutOfStock.setPrice(new BigDecimal("70.00"));
    productOutOfStock.setStatus(ProductStatus.PUBLISHED);
    productOutOfStock.setStockQuantity(0);
    productOutOfStock.setDeleted(false);

    productDeleted = new Product();
    productDeleted.setId(50L);
    productDeleted.setName("Sản phẩm đã xóa");
    productDeleted.setPrice(new BigDecimal("30.00"));
    productDeleted.setStatus(ProductStatus.PUBLISHED);
    productDeleted.setStockQuantity(5);
    productDeleted.setDeleted(true);

    cartItemEntity1 = new CartItem();
    cartItemEntity1.setId(100L);
    cartItemEntity1.setUser(testUser);
    cartItemEntity1.setProduct(product1);
    cartItemEntity1.setQuantity(2);

    cartItemEntity2 = new CartItem();
    cartItemEntity2.setId(200L);
    cartItemEntity2.setUser(testUser);
    cartItemEntity2.setProduct(product2);
    cartItemEntity2.setQuantity(1);

    cartItemUnavailableEntity = new CartItem();
    cartItemUnavailableEntity.setId(300L);
    cartItemUnavailableEntity.setUser(testUser);
    cartItemUnavailableEntity.setProduct(productUnavailable);
    cartItemUnavailableEntity.setQuantity(1);

    cartItemOutOfStockEntity = new CartItem();
    cartItemOutOfStockEntity.setId(400L);
    cartItemOutOfStockEntity.setUser(testUser);
    cartItemOutOfStockEntity.setProduct(productOutOfStock);
    cartItemOutOfStockEntity.setQuantity(3);

    cartItemDeletedProductEntity = new CartItem();
    cartItemDeletedProductEntity.setId(500L);
    cartItemDeletedProductEntity.setUser(testUser);
    cartItemDeletedProductEntity.setProduct(productDeleted);
    cartItemDeletedProductEntity.setQuantity(1);

    cartItemResponse1 = new CartItemResponse();
    cartItemResponse1.setId(100L);
    cartItemResponse1.setQuantity(2);
    cartItemResponse1.setItemTotal(new BigDecimal("200.00"));

    cartItemResponse2 = new CartItemResponse();
    cartItemResponse2.setId(200L);
    cartItemResponse2.setQuantity(1);
    cartItemResponse2.setItemTotal(new BigDecimal("200.00"));
  }

  // SỬA LỖI: Thêm tearDown để đóng mock static sau mỗi test
  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  @Nested
  @DisplayName("Get Cart Tests")
  class GetCartTests {
    @Test
    @DisplayName("Get Cart - Success with Valid Items")
    void getCart_whenAllItemsValid_shouldReturnCartResponse() {
      when(cartItemRepository.findByUserId(testUser.getId()))
          .thenReturn(List.of(cartItemEntity1, cartItemEntity2));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
      when(cartItemMapper.toCartItemResponse(cartItemEntity1)).thenReturn(cartItemResponse1);
      when(cartItemMapper.toCartItemResponse(cartItemEntity2)).thenReturn(cartItemResponse2);

      CartResponse result = cartService.getCart(authentication);

      assertNotNull(result);
      assertEquals(2, result.getItems().size());
      assertEquals(new BigDecimal("400.00"), result.getSubTotal());
      assertEquals(3, result.getTotalItems());
      assertTrue(result.getAdjustments().isEmpty());
      verify(cartItemRepository, never()).saveAll(anyList());
      verify(cartItemRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    @DisplayName("Get Cart - Item Product Not Found (Soft-Deleted or Does Not Exist)")
    void getCart_whenItemProductNotFound_shouldRemoveItemAndAddAdjustment() {
      when(cartItemRepository.findByUserId(testUser.getId()))
          .thenReturn(List.of(cartItemDeletedProductEntity));
      when(productRepository.findById(productDeleted.getId())).thenReturn(Optional.empty());

      CartResponse result = cartService.getCart(authentication);

      assertNotNull(result);
      assertTrue(result.getItems().isEmpty());
      assertEquals(BigDecimal.ZERO, result.getSubTotal());
      assertEquals(0, result.getTotalItems());
      assertEquals(1, result.getAdjustments().size());
      assertEquals("REMOVED", result.getAdjustments().get(0).getType());
      assertTrue(result.getAdjustments().get(0).getMessage().contains(productDeleted.getName()));
      verify(cartItemRepository)
          .deleteAllByIdInBatch(List.of(cartItemDeletedProductEntity.getId()));
    }

    @Test
    @DisplayName("Get Cart - Item Product Not Published")
    void getCart_whenItemProductNotPublished_shouldRemoveItemAndAddAdjustment() {
      when(cartItemRepository.findByUserId(testUser.getId()))
          .thenReturn(List.of(cartItemUnavailableEntity));
      when(productRepository.findById(productUnavailable.getId()))
          .thenReturn(Optional.of(productUnavailable));

      CartResponse result = cartService.getCart(authentication);

      assertTrue(result.getItems().isEmpty());
      assertEquals(1, result.getAdjustments().size());
      assertTrue(
          result.getAdjustments().get(0).getMessage().contains(productUnavailable.getName()));
      verify(cartItemRepository).deleteAllByIdInBatch(List.of(cartItemUnavailableEntity.getId()));
    }

    @Test
    @DisplayName("Get Cart - Item Out Of Stock")
    void getCart_whenItemOutOfStock_shouldRemoveItemAndAddAdjustment() {
      when(cartItemRepository.findByUserId(testUser.getId()))
          .thenReturn(List.of(cartItemOutOfStockEntity));
      when(productRepository.findById(productOutOfStock.getId()))
          .thenReturn(Optional.of(productOutOfStock));

      CartResponse result = cartService.getCart(authentication);

      assertTrue(result.getItems().isEmpty());
      assertEquals(1, result.getAdjustments().size());
      assertTrue(result.getAdjustments().get(0).getMessage().contains(productOutOfStock.getName()));
      verify(cartItemRepository).deleteAllByIdInBatch(List.of(cartItemOutOfStockEntity.getId()));
    }

    @Test
    @DisplayName("Get Cart - Item Quantity Exceeds Stock, Adjusts Quantity")
    void getCart_whenItemQuantityExceedsStock_shouldAdjustQuantityAndAddAdjustment() {
      product1.setStockQuantity(1);
      cartItemEntity1.setQuantity(2);

      CartItemResponse adjustedResponse = new CartItemResponse();
      adjustedResponse.setId(cartItemEntity1.getId());
      adjustedResponse.setQuantity(1);
      adjustedResponse.setItemTotal(product1.getPrice().multiply(BigDecimal.ONE));

      when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(List.of(cartItemEntity1));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(cartItemMapper.toCartItemResponse(
              argThat(ci -> ci.getId().equals(cartItemEntity1.getId()) && ci.getQuantity() == 1)))
          .thenReturn(adjustedResponse);

      CartResponse result = cartService.getCart(authentication);

      assertEquals(1, result.getItems().size());
      assertEquals(1, result.getItems().get(0).getQuantity());
      assertEquals(1, result.getAdjustments().size());
      assertEquals("ADJUSTED", result.getAdjustments().get(0).getType());
      assertTrue(result.getAdjustments().get(0).getMessage().contains(product1.getName()));
      verify(cartItemRepository).saveAll(List.of(cartItemEntity1));
    }

    @Test
    @DisplayName("Get Cart - Empty Cart")
    void getCart_whenCartIsEmpty_shouldReturnEmptyCartResponse() {
      when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(Collections.emptyList());

      CartResponse result = cartService.getCart(authentication);

      assertTrue(result.getItems().isEmpty());
      assertEquals(BigDecimal.ZERO, result.getSubTotal());
      assertEquals(0, result.getTotalItems());
      assertTrue(result.getAdjustments().isEmpty());
    }
  }

  @Nested
  @DisplayName("Add Item Tests")
  class AddItemTests {
    @Test
    @DisplayName("Add Item - New Product to Cart - Success")
    void addItem_whenNewProduct_shouldSaveCartItem() {
      CartItemRequest request = new CartItemRequest();
      request.setProductId(product1.getId());
      request.setQuantity(1);

      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(cartItemRepository.findByUserIdAndProductId(testUser.getId(), product1.getId()))
          .thenReturn(Optional.empty());
      when(cartItemRepository.save(any(CartItem.class)))
          .thenAnswer(
              invocation -> {
                CartItem itemToSave = invocation.getArgument(0);
                itemToSave.setId(300L);
                return itemToSave;
              });
      cartItemResponse1.setQuantity(1);
      cartItemResponse1.setItemTotal(product1.getPrice());
      when(cartItemMapper.toCartItemResponse(any(CartItem.class))).thenReturn(cartItemResponse1);

      CartItemResponse result = cartService.addItem(authentication, request);

      assertNotNull(result);
      assertEquals(1, result.getQuantity());
      verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("Add Item - Existing Product in Cart - Updates Quantity")
    void addItem_whenExistingProduct_shouldUpdateQuantity() {
      CartItemRequest request = new CartItemRequest();
      request.setProductId(product1.getId());
      request.setQuantity(1);

      cartItemEntity1.setQuantity(2);
      product1.setStockQuantity(10);

      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(cartItemRepository.findByUserIdAndProductId(testUser.getId(), product1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));
      when(cartItemRepository.save(any(CartItem.class))).thenReturn(cartItemEntity1);

      CartItemResponse updatedResponse = new CartItemResponse();
      updatedResponse.setId(cartItemEntity1.getId());
      updatedResponse.setQuantity(3);
      updatedResponse.setItemTotal(product1.getPrice().multiply(BigDecimal.valueOf(3)));
      when(cartItemMapper.toCartItemResponse(cartItemEntity1)).thenReturn(updatedResponse);

      CartItemResponse result = cartService.addItem(authentication, request);

      assertNotNull(result);
      assertEquals(3, result.getQuantity());
      verify(cartItemRepository).save(cartItemEntity1);
    }

    @Test
    @DisplayName("Add Item - Product Not Found - Throws BadRequestException")
    void addItem_whenProductNotFound_shouldThrowBadRequestException() {
      CartItemRequest request = new CartItemRequest();
      request.setProductId(99L);
      request.setQuantity(1);
      when(productRepository.findById(99L)).thenReturn(Optional.empty());

      BadRequestException exception =
          assertThrows(
              BadRequestException.class, () -> cartService.addItem(authentication, request));
      assertTrue(
          exception.getMessage().contains("Sản phẩm bạn chọn không tồn tại hoặc đã ngừng bán."));
    }

    @Test
    @DisplayName("Add Item - Product Not Published - Throws BadRequestException")
    void addItem_whenProductNotPublished_shouldThrowBadRequestException() {
      CartItemRequest request = new CartItemRequest();
      request.setProductId(productUnavailable.getId());
      request.setQuantity(1);
      when(productRepository.findById(productUnavailable.getId()))
          .thenReturn(Optional.of(productUnavailable));

      BadRequestException exception =
          assertThrows(
              BadRequestException.class, () -> cartService.addItem(authentication, request));
      assertEquals(
          "Product is not available: " + productUnavailable.getName(), exception.getMessage());
    }

    @Test
    @DisplayName("Add Item - Not Enough Stock (New Item) - Throws OutOfStockException")
    void addItem_whenNotEnoughStockForNewItem_shouldThrowOutOfStockException() {
      CartItemRequest request = new CartItemRequest();
      request.setProductId(product1.getId());
      request.setQuantity(11);
      product1.setStockQuantity(10);

      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

      OutOfStockException exception =
          assertThrows(
              OutOfStockException.class, () -> cartService.addItem(authentication, request));
      assertEquals("Not enough stock for product: " + product1.getName(), exception.getMessage());
      assertEquals(10, exception.getAvailableStock());
    }

    @Test
    @DisplayName("Add Item - Not Enough Stock (Existing Item) - Throws OutOfStockException")
    void addItem_whenNotEnoughStockForExistingItem_shouldThrowOutOfStockException() {
      CartItemRequest request = new CartItemRequest();
      request.setProductId(product1.getId());
      request.setQuantity(5);

      cartItemEntity1.setQuantity(6);
      product1.setStockQuantity(10);

      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(cartItemRepository.findByUserIdAndProductId(testUser.getId(), product1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));

      OutOfStockException exception =
          assertThrows(
              OutOfStockException.class, () -> cartService.addItem(authentication, request));
      assertTrue(
          exception.getMessage().contains("Not enough stock for product: " + product1.getName()));
      assertEquals(10, exception.getAvailableStock());
    }
  }

  @Nested
  @DisplayName("Update Item Quantity Tests")
  class UpdateItemQuantityTests {
    @Test
    @DisplayName("Update Item Quantity - Success")
    void updateItemQuantity_success() {
      CartItemUpdateRequest request = new CartItemUpdateRequest();
      request.setQuantity(5);
      product1.setStockQuantity(10);

      when(cartItemRepository.findById(cartItemEntity1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(cartItemRepository.save(any(CartItem.class))).thenReturn(cartItemEntity1);

      CartItemResponse updatedResponse = new CartItemResponse();
      updatedResponse.setId(cartItemEntity1.getId());
      updatedResponse.setQuantity(5);
      when(cartItemMapper.toCartItemResponse(cartItemEntity1)).thenReturn(updatedResponse);

      CartItemResponse result =
          cartService.updateItemQuantity(authentication, cartItemEntity1.getId(), request);

      assertNotNull(result);
      assertEquals(5, result.getQuantity());
      verify(cartItemRepository).save(cartItemEntity1);
    }

    @Test
    @DisplayName("Update Item Quantity - Cart Item Not Found")
    void updateItemQuantity_whenCartItemNotFound_shouldThrowResourceNotFound() {
      CartItemUpdateRequest request = new CartItemUpdateRequest();
      request.setQuantity(1);
      when(cartItemRepository.findById(999L)).thenReturn(Optional.empty());

      assertThrows(
          ResourceNotFoundException.class,
          () -> cartService.updateItemQuantity(authentication, 999L, request));
    }

    @Test
    @DisplayName("Update Item Quantity - Not Owned By User")
    void updateItemQuantity_whenNotOwnedByUser_shouldThrowAccessDenied() {
      CartItemUpdateRequest request = new CartItemUpdateRequest();
      request.setQuantity(1);
      User anotherUser = new User();
      anotherUser.setId(2L);
      cartItemEntity1.setUser(anotherUser);

      when(cartItemRepository.findById(cartItemEntity1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));

      assertThrows(
          AccessDeniedException.class,
          () -> cartService.updateItemQuantity(authentication, cartItemEntity1.getId(), request));
    }

    @Test
    @DisplayName("Update Item Quantity - Product Becomes Unavailable, Removes Item")
    void updateItemQuantity_whenProductBecomesUnavailable_shouldRemoveItemAndThrowBadRequest() {
      CartItemUpdateRequest request = new CartItemUpdateRequest();
      request.setQuantity(1);
      product1.setStatus(ProductStatus.UNPUBLISHED);

      when(cartItemRepository.findById(cartItemEntity1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () ->
                  cartService.updateItemQuantity(authentication, cartItemEntity1.getId(), request));
      assertTrue(
          exception
              .getMessage()
              .contains("Sản phẩm trong giỏ hàng không còn được bán và đã được xóa."));
      verify(cartItemRepository).delete(cartItemEntity1);
    }

    @Test
    @DisplayName("Update Item Quantity - Not Enough Stock")
    void updateItemQuantity_whenNotEnoughStock_shouldThrowOutOfStockException() {
      CartItemUpdateRequest request = new CartItemUpdateRequest();
      request.setQuantity(11);
      product1.setStockQuantity(10);

      when(cartItemRepository.findById(cartItemEntity1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

      OutOfStockException exception =
          assertThrows(
              OutOfStockException.class,
              () ->
                  cartService.updateItemQuantity(authentication, cartItemEntity1.getId(), request));
      assertEquals("Not enough stock for product: " + product1.getName(), exception.getMessage());
      assertEquals(10, exception.getAvailableStock());
    }
  }

  @Nested
  @DisplayName("Remove Item and Clear Cart Tests")
  class RemoveAndClearTests {
    @Test
    @DisplayName("Remove Item - Success")
    void removeItem_success() {
      when(cartItemRepository.findById(cartItemEntity1.getId()))
          .thenReturn(Optional.of(cartItemEntity1));
      doNothing().when(cartItemRepository).delete(cartItemEntity1);

      assertDoesNotThrow(() -> cartService.removeItem(authentication, cartItemEntity1.getId()));
      verify(cartItemRepository).delete(cartItemEntity1);
    }

    @Test
    @DisplayName("Clear Cart - Success")
    void clearCart_success() {
      doNothing().when(cartItemRepository).deleteByUserId(testUser.getId());
      assertDoesNotThrow(() -> cartService.clearCart(authentication));
      verify(cartItemRepository).deleteByUserId(testUser.getId());
    }
  }

  @Nested
  @DisplayName("Validate Cart For Checkout Tests")
  class ValidateCartTests {
    @Test
    @DisplayName("Validate Cart - All Items Valid")
    void validateCartForCheckout_whenAllItemsValid_shouldReturnValidResponse() {
      when(cartItemRepository.findByUserId(testUser.getId()))
          .thenReturn(List.of(cartItemEntity1, cartItemEntity2));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));

      CartValidationResponse response = cartService.validateCartForCheckout(authentication);

      assertTrue(response.isValid());
      assertTrue(
          response.getMessages().stream().anyMatch(m -> m.contains("Giỏ hàng của bạn hợp lệ")));
      assertTrue(response.getAdjustments().isEmpty());
    }

    @Test
    @DisplayName("Validate Cart - Item Quantity Adjusted")
    void validateCartForCheckout_whenItemQuantityAdjusted_shouldReturnInvalidWithAdjustments() {
      product1.setStockQuantity(1);
      cartItemEntity1.setQuantity(2);

      when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(List.of(cartItemEntity1));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

      CartValidationResponse response = cartService.validateCartForCheckout(authentication);

      assertFalse(response.isValid());
      assertEquals(1, response.getAdjustments().size());
      assertEquals("ADJUSTED", response.getAdjustments().get(0).getType());
      assertTrue(
          response
              .getAdjustments()
              .get(0)
              .getMessage()
              .contains("Số lượng sản phẩm '" + product1.getName() + "' đã được cập nhật thành 1"));
      verify(cartItemRepository).saveAllAndFlush(List.of(cartItemEntity1));
    }

    @Test
    @DisplayName("Validate Cart - Item Removed (Out Of Stock)")
    void validateCartForCheckout_whenItemRemoved_shouldReturnInvalidWithAdjustments() {
      when(cartItemRepository.findByUserId(testUser.getId()))
          .thenReturn(List.of(cartItemOutOfStockEntity));
      when(productRepository.findById(productOutOfStock.getId()))
          .thenReturn(Optional.of(productOutOfStock));

      CartValidationResponse response = cartService.validateCartForCheckout(authentication);

      assertFalse(response.isValid());
      assertEquals(1, response.getAdjustments().size());
      assertEquals("REMOVED", response.getAdjustments().get(0).getType());
      assertTrue(
          response
              .getAdjustments()
              .get(0)
              .getMessage()
              .contains("Sản phẩm '" + productOutOfStock.getName() + "' đã hết hàng"));
      verify(cartItemRepository).deleteAllByIdInBatch(List.of(cartItemOutOfStockEntity.getId()));
    }

    @Test
    @DisplayName("Validate Cart - Empty Cart")
    void validateCartForCheckout_whenCartIsEmpty_shouldReturnValidWithEmptyMessage() {
      when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(Collections.emptyList());
      CartValidationResponse response = cartService.validateCartForCheckout(authentication);
      assertTrue(response.isValid());
      assertTrue(
          response.getMessages().stream().anyMatch(m -> m.contains("Giỏ hàng của bạn đang trống")));
      assertTrue(response.getAdjustments().isEmpty());
    }
  }

  @Test
  @DisplayName("Get User From Authentication - User Not Found - Throws UsernameNotFoundException")
  void getUserFromAuthentication_whenUserNotFound_shouldThrowUsernameNotFoundException() {
    // Mock SecurityUtils để nó ném lỗi, đây là cách test đúng
    mockedSecurityUtils
        .when(SecurityUtils::getCurrentAuthenticatedUser)
        .thenThrow(new UsernameNotFoundException("User not found"));

    assertThrows(UsernameNotFoundException.class, () -> cartService.getCart(authentication));
  }

  @Test
  @DisplayName("Get User From Authentication - Not Authenticated - Throws AccessDeniedException")
  void getUserFromAuthentication_whenNotAuthenticated_shouldThrowAccessDeniedException() {
    // Mock SecurityUtils để nó ném lỗi
    mockedSecurityUtils
        .when(SecurityUtils::getCurrentAuthenticatedUser)
        .thenThrow(new AccessDeniedException("Not authenticated"));

    assertThrows(AccessDeniedException.class, () -> cartService.getCart(authentication));
  }
}
