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
import com.yourcompany.agritrade.ordering.dto.response.CartValidationResponse;
import com.yourcompany.agritrade.ordering.mapper.CartItemMapper;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CartItemMapper cartItemMapper;
    @Mock private Authentication authentication;

    @InjectMocks
    private CartServiceImpl cartService;

    private User testUser;
    private Product product1, product2;
    private CartItem cartItemEntity1, cartItemEntity2;
    private CartItemResponse cartItemResponse1, cartItemResponse2;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("cartuser@example.com");

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

        cartItemResponse1 = new CartItemResponse(); // Giả sử mapper tạo ra
        cartItemResponse1.setId(100L);
        cartItemResponse1.setQuantity(2);
        // ... set product summary và itemTotal cho cartItemResponse1

        cartItemResponse2 = new CartItemResponse();
        cartItemResponse2.setId(200L);
        cartItemResponse2.setQuantity(1);
        // ... set product summary và itemTotal cho cartItemResponse2

        lenient().when(authentication.getName()).thenReturn(testUser.getEmail());
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("Get Cart - Success with Items and Adjustments")
    void getCart_whenItemsExistAndSomeNeedAdjustment_shouldReturnCartWithAdjustments() {
        // Arrange
        Product unavailableProduct = new Product(); // Sản phẩm không còn bán
        unavailableProduct.setId(30L);
        unavailableProduct.setName("Sản phẩm đã ẩn");
        unavailableProduct.setStatus(ProductStatus.UNPUBLISHED);

        CartItem cartItemUnavailable = new CartItem();
        cartItemUnavailable.setId(300L);
        cartItemUnavailable.setUser(testUser);
        cartItemUnavailable.setProduct(unavailableProduct);
        cartItemUnavailable.setQuantity(1);

        Product outOfStockProduct = new Product(); // Sản phẩm hết hàng
        outOfStockProduct.setId(40L);
        outOfStockProduct.setName("Sản phẩm hết hàng");
        outOfStockProduct.setPrice(new BigDecimal("50.00"));
        outOfStockProduct.setStatus(ProductStatus.PUBLISHED);
        outOfStockProduct.setStockQuantity(0); // Hết hàng

        CartItem cartItemOutOfStock = new CartItem();
        cartItemOutOfStock.setId(400L);
        cartItemOutOfStock.setUser(testUser);
        cartItemOutOfStock.setProduct(outOfStockProduct);
        cartItemOutOfStock.setQuantity(3);


        // Giả sử cartItemEntity1 (product1) có số lượng vượt tồn kho
        product1.setStockQuantity(1); // Chỉ còn 1
        cartItemEntity1.setQuantity(2); // Nhưng trong giỏ là 2

        List<CartItem> cartItemsFromDb = new ArrayList<>(List.of(cartItemEntity1, cartItemUnavailable, cartItemOutOfStock));
        when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(cartItemsFromDb);

        // Mock productRepository.findById cho từng sản phẩm
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findById(unavailableProduct.getId())).thenReturn(Optional.of(unavailableProduct)); // Vẫn tìm thấy nhưng status khác
        when(productRepository.findById(outOfStockProduct.getId())).thenReturn(Optional.of(outOfStockProduct));


        // Mock mapper cho item hợp lệ (sau khi điều chỉnh)
        CartItemResponse adjustedCartItem1Response = new CartItemResponse();
        adjustedCartItem1Response.setId(cartItemEntity1.getId());
        adjustedCartItem1Response.setQuantity(1); // Số lượng đã điều chỉnh
        // ... (set product summary và itemTotal cho adjustedCartItem1Response)
        when(cartItemMapper.toCartItemResponse(argThat(ci -> ci.getId().equals(cartItemEntity1.getId()) && ci.getQuantity() == 1)))
                .thenReturn(adjustedCartItem1Response);


        // Act
        CartResponse cartResponse = cartService.getCart(authentication);

        // Assert
        assertNotNull(cartResponse);
        assertEquals(1, cartResponse.getItems().size(), "Chỉ còn item hợp lệ sau điều chỉnh");
        assertEquals(adjustedCartItem1Response.getId(), cartResponse.getItems().get(0).getId());
        assertEquals(1, cartResponse.getItems().get(0).getQuantity());

        assertNotNull(cartResponse.getAdjustments());
        assertEquals(3, cartResponse.getAdjustments().size(), "Phải có 3 điều chỉnh");

        assertTrue(cartResponse.getAdjustments().stream().anyMatch(adj -> adj.getMessage().contains("Số lượng sản phẩm '" + product1.getName() + "' đã được cập nhật thành 1")));
        assertTrue(cartResponse.getAdjustments().stream().anyMatch(adj -> adj.getMessage().contains("Sản phẩm '" + unavailableProduct.getName() + "' không còn được bán")));
        assertTrue(cartResponse.getAdjustments().stream().anyMatch(adj -> adj.getMessage().contains("Sản phẩm '" + outOfStockProduct.getName() + "' đã hết hàng")));


    }

    @Test
    @DisplayName("Add Item - New Product to Cart - Success")
    void addItem_whenNewProduct_shouldSaveCartItem() {
        // Arrange
        CartItemRequest request = new CartItemRequest();
        request.setProductId(product1.getId());
        request.setQuantity(1);

        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(cartItemRepository.findByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(Optional.empty());
        // Giả lập cartItemRepository.save trả về CartItem đã được gán ID
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem itemToSave = invocation.getArgument(0);
            itemToSave.setId(300L); // Gán ID giả lập
            return itemToSave;
        });
        when(cartItemMapper.toCartItemResponse(any(CartItem.class))).thenReturn(cartItemResponse1); // Giả sử response1 cho item mới

        // Act
        CartItemResponse result = cartService.addItem(authentication, request);

        // Assert
        assertNotNull(result);
        assertEquals(cartItemResponse1.getId(), result.getId());
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("Add Item - Existing Product in Cart - Updates Quantity")
    void addItem_whenExistingProduct_shouldUpdateQuantity() {
        // Arrange
        CartItemRequest request = new CartItemRequest();
        request.setProductId(product1.getId());
        request.setQuantity(1); // Thêm 1 nữa

        cartItemEntity1.setQuantity(2); // Giả sử trong giỏ đã có 2
        product1.setStockQuantity(10); // Đủ hàng

        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(cartItemRepository.findByUserIdAndProductId(testUser.getId(), product1.getId())).thenReturn(Optional.of(cartItemEntity1));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(cartItemEntity1); // Trả về item đã cập nhật
        // Giả sử mapper trả về response với quantity mới
        CartItemResponse updatedResponse = new CartItemResponse();
        updatedResponse.setId(cartItemEntity1.getId());
        updatedResponse.setQuantity(3); // 2 (cũ) + 1 (mới)
        when(cartItemMapper.toCartItemResponse(cartItemEntity1)).thenReturn(updatedResponse);


        // Act
        CartItemResponse result = cartService.addItem(authentication, request);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getQuantity());
        verify(cartItemRepository).save(cartItemEntity1); // Kiểm tra save với đối tượng đã cập nhật
    }




    @Test
    @DisplayName("Validate Cart - All Items Valid - Returns Valid Response")
    void validateCartForCheckout_whenAllItemsValid_shouldReturnValidResponse() {
        // Arrange
        when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(List.of(cartItemEntity1, cartItemEntity2));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1)); // product1 còn hàng
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2)); // product2 còn hàng

        // Act
        CartValidationResponse response = cartService.validateCartForCheckout(authentication);

        // Assert
        assertTrue(response.isValid());
        assertTrue(response.getMessages().stream().anyMatch(m -> m.contains("Giỏ hàng của bạn hợp lệ")));
        assertTrue(response.getAdjustments().isEmpty());
        verify(cartItemRepository, never()).saveAll(anyList());
        verify(cartItemRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    @DisplayName("Validate Cart - Some Items Adjusted/Removed - Returns Invalid Response with Adjustments")
    void validateCartForCheckout_whenItemsAdjustedOrRemoved_shouldReturnInvalidResponseWithDetails() {
        // Arrange
        product1.setStockQuantity(1); // Chỉ còn 1, trong giỏ đang là 2 (cartItemEntity1)
        product2.setStatus(ProductStatus.UNPUBLISHED); // Sản phẩm 2 không còn bán

        when(cartItemRepository.findByUserId(testUser.getId())).thenReturn(List.of(cartItemEntity1, cartItemEntity2));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));

        // Act
        CartValidationResponse response = cartService.validateCartForCheckout(authentication);

        // Assert
        assertFalse(response.isValid(), "Cart should be invalid due to adjustments/removals");
        assertEquals(2, response.getAdjustments().size());

        Optional<CartAdjustmentInfo> adjustment1 = response.getAdjustments().stream()
                .filter(adj -> adj.getProductId() != null && adj.getProductId().equals(product1.getId()))
                .findFirst();
        assertTrue(adjustment1.isPresent());
        assertEquals("ADJUSTED", adjustment1.get().getType());
        assertTrue(adjustment1.get().getMessage().contains("Số lượng sản phẩm '" + product1.getName() + "' đã được cập nhật thành 1"));

        Optional<CartAdjustmentInfo> adjustment2 = response.getAdjustments().stream()
                .filter(adj -> adj.getProductId() != null && adj.getProductId().equals(product2.getId()))
                .findFirst();
        assertTrue(adjustment2.isPresent());
        assertEquals("REMOVED", adjustment2.get().getType());
        assertTrue(adjustment2.get().getMessage().contains("Sản phẩm '" + product2.getName() + "' không còn được bán"));


    }


    // TODO: Viết thêm Unit Test cho các phương thức khác của CartServiceImpl:
    // - updateItemQuantity (thành công, item không thuộc user, sản phẩm hết hàng khi cập nhật)
    // - removeItem (thành công, item không thuộc user)
    // - clearCart
}