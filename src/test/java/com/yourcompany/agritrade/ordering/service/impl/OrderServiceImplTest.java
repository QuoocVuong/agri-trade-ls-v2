package com.yourcompany.agritrade.ordering.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.*;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.*;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import com.yourcompany.agritrade.ordering.service.PaymentGatewayService;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

  @Mock private OrderRepository orderRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private CartItemRepository cartItemRepository;
  @Mock private ProductRepository productRepository;
  @Mock private UserRepository userRepository;
  @Mock private AddressRepository addressRepository;
  @Mock private FarmerProfileRepository farmerProfileRepository;
  @Mock private OrderMapper orderMapper;
  @Mock private NotificationService notificationService;
  @Mock private InvoiceService invoiceService;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private FileStorageService fileStorageService;

  @Mock
  @Qualifier("vnPayService")
  private PaymentGatewayService vnPayService;

  @Mock
  @Qualifier("moMoService")
  private PaymentGatewayService moMoService;

  @Mock private HttpServletRequest httpServletRequest;
  @Mock private Authentication authentication;

  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private OrderServiceImpl orderService;

  private User testBuyer, testFarmer, testAdmin;
  private Product product1, product2;
  private Address shippingAddress;
  private FarmerProfile farmerProfile;
  private CheckoutRequest checkoutRequest;
  private Order orderEntity;
  private OrderResponse orderResponseDto;
  private OrderSummaryResponse orderSummaryResponseDto;

  @BeforeEach
  void setUp() {
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    testBuyer =
        User.builder()
            .id(1L)
            .email("buyer@example.com")
            .fullName("Test Buyer")
            .roles(Set.of(new Role(RoleType.ROLE_CONSUMER)))
            .build();
    testFarmer =
        User.builder()
            .id(2L)
            .email("farmer@example.com")
            .fullName("Test Farmer")
            .roles(Set.of(new Role(RoleType.ROLE_FARMER)))
            .build();
    testAdmin =
        User.builder()
            .id(3L)
            .email("admin@example.com")
            .fullName("Test Admin")
            .roles(Set.of(new Role(RoleType.ROLE_ADMIN)))
            .build();

    farmerProfile = new FarmerProfile();
    farmerProfile.setUserId(testFarmer.getId());
    farmerProfile.setUser(testFarmer);
    farmerProfile.setProvinceCode("20");
    farmerProfile.setFarmName("Test Farm");
    testFarmer.setFarmerProfile(farmerProfile);

    product1 =
        Product.builder()
            .id(10L)
            .name("Sản phẩm A")
            .price(new BigDecimal("100.00"))
            .stockQuantity(10)
            .status(ProductStatus.PUBLISHED)
            .farmer(testFarmer)
            .unit("kg")
            .provinceCode("20")
            .version(0L)
            .images(new HashSet<>())
            .build();
    product2 =
        Product.builder()
            .id(20L)
            .name("Sản phẩm B")
            .price(new BigDecimal("50.00"))
            .stockQuantity(5)
            .status(ProductStatus.PUBLISHED)
            .farmer(testFarmer)
            .unit("cái")
            .provinceCode("20")
            .version(0L)
            .images(new HashSet<>())
            .build();

    ProductImage productImage1 = new ProductImage();
    productImage1.setId(100L);
    productImage1.setProduct(product1);
    productImage1.setBlobPath("product1/image.jpg");
    productImage1.setDefault(true);
    product1.getImages().add(productImage1);

    shippingAddress = new Address();
    shippingAddress.setId(100L);
    shippingAddress.setUser(testBuyer);
    shippingAddress.setFullName("Recipient Name");
    shippingAddress.setPhoneNumber("0123456789");
    shippingAddress.setAddressDetail("123 Main St");
    shippingAddress.setProvinceCode("20");
    shippingAddress.setDistrictCode("180");
    shippingAddress.setWardCode("06289");

    checkoutRequest = new CheckoutRequest();
    checkoutRequest.setShippingAddressId(shippingAddress.getId());
    checkoutRequest.setPaymentMethod(PaymentMethod.COD);
    checkoutRequest.setNotes("Test order notes");
    checkoutRequest.setConfirmedTotalAmount(new BigDecimal("15250.00"));

    orderEntity = new Order();
    orderEntity.setId(1L);
    orderEntity.setOrderCode("ORD-SAMPLE-001");
    orderEntity.setBuyer(testBuyer);
    orderEntity.setFarmer(testFarmer);
    orderEntity.setStatus(OrderStatus.PENDING);
    orderEntity.setPaymentMethod(PaymentMethod.COD);
    orderEntity.setPaymentStatus(PaymentStatus.PENDING);
    orderEntity.setTotalAmount(new BigDecimal("250.00"));
    orderEntity.setOrderItems(new HashSet<>());
    OrderItem oi1 = new OrderItem();
    oi1.setProduct(product1);
    oi1.setQuantity(2);
    oi1.setPricePerUnit(product1.getPrice());
    oi1.setTotalPrice(product1.getPrice().multiply(BigDecimal.valueOf(2)));
    orderEntity.addOrderItem(oi1);

    orderResponseDto = new OrderResponse();
    orderResponseDto.setId(1L);
    orderResponseDto.setOrderCode("ORD-SAMPLE-001");

    orderSummaryResponseDto = new OrderSummaryResponse();
    orderSummaryResponseDto.setId(1L);
    orderSummaryResponseDto.setOrderCode("ORD-SAMPLE-001");

    lenient()
        .when(fileStorageService.getFileUrl(anyString()))
        .thenAnswer(invocation -> "mockedUrl/" + invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  private void mockAuthenticatedUser(User user) {
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(user);
    lenient()
        .when(SecurityUtils.hasRole(anyString()))
        .thenAnswer(
            invocation -> {
              String roleName = invocation.getArgument(0);
              return user.getRoles().stream().anyMatch(r -> r.getName().name().equals(roleName));
            });
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    user.getRoles()
        .forEach(role -> authorities.add(new SimpleGrantedAuthority(role.getName().name())));
    lenient().when(authentication.getAuthorities()).thenReturn((Collection) authorities);
  }

  @Nested
  @DisplayName("Checkout Tests")
  class CheckoutTests {
    @Test
    @DisplayName("Checkout - Success")
    void checkout_success() {
      mockAuthenticatedUser(testBuyer);

      // 1. Dữ liệu đầu vào
      CartItem ci1 = new CartItem();
      ci1.setUser(testBuyer);
      ci1.setProduct(product1);
      ci1.setQuantity(2);

      CartItem ci2 = new CartItem();
      ci2.setUser(testBuyer);
      ci2.setProduct(product2);
      ci2.setQuantity(1);

      List<CartItem> cartItems = List.of(ci1, ci2);

      // Tạo một đối tượng Order mẫu mà chúng ta mong đợi sẽ được lưu
      Order expectedOrderToBeSaved = new Order();
      expectedOrderToBeSaved.setId(123L); // Gán một ID giả định
      expectedOrderToBeSaved.setOrderCode("MOCKED-CODE-123");

      // 2. Thiết lập hành vi cho các Mock (đơn giản hóa)
      when(addressRepository.findByIdAndUserId(shippingAddress.getId(), testBuyer.getId()))
          .thenReturn(Optional.of(shippingAddress));
      when(cartItemRepository.findByUserId(testBuyer.getId())).thenReturn(cartItems);
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
      when(userRepository.findById(testFarmer.getId())).thenReturn(Optional.of(testFarmer));
      when(farmerProfileRepository.findById(testFarmer.getId()))
          .thenReturn(Optional.of(farmerProfile));

      // Đơn giản hóa mock save: Khi gọi save, trả về đối tượng order đã chuẩn bị sẵn
      when(orderRepository.save(any(Order.class))).thenReturn(expectedOrderToBeSaved);

      // Đơn giản hóa mock findById: Khi được gọi để map kết quả, trả về chính order đó
      when(orderRepository.findById(expectedOrderToBeSaved.getId()))
          .thenReturn(Optional.of(expectedOrderToBeSaved));

      // Mock mapper để trả về DTO
      when(orderMapper.toOrderResponse(any(Order.class))).thenReturn(orderResponseDto);

      // ** Quan trọng: Sửa lại checkoutRequest để khớp với tính toán thực tế, tránh lỗi giá không
      // khớp **
      // SubTotal = (100.00 * 2) + (50.00 * 1) = 250.00
      // ShippingFee (giả sử là 15000.00 như trong logic code) = 15000.00
      // Discount (giả sử là 0) = 0
      // Total = 250.00 + 15000.00 = 15250.00
      // LƯU Ý: Giá trong code đang là BigDecimal, bạn phải dùng new BigDecimal("số_dạng_chuỗi")
      BigDecimal expectedTotal =
          new BigDecimal("265.00"); // (2 * 100) + (1 * 50) + 15.00 (phí ship giả định)
      checkoutRequest.setConfirmedTotalAmount(
          new BigDecimal("15250.00")); // Giữ giá trị này khớp với logic tính phí ship của bạn

      // 3. Gọi phương thức cần test
      List<OrderResponse> result = orderService.checkout(authentication, checkoutRequest);

      // 4. Kiểm tra kết quả
      assertNotNull(result);
      assertEquals(1, result.size());

      // Kiểm tra các tương tác quan trọng
      verify(cartItemRepository).deleteAllInBatch(cartItems);
      verify(productRepository, times(2)).saveAndFlush(any(Product.class));
      verify(notificationService).sendOrderPlacementNotification(any(Order.class));
      verify(paymentRepository).save(any(Payment.class));
      verify(orderRepository).save(any(Order.class)); // Chỉ cần kiểm tra nó được gọi là đủ
    }

    @Test
    @DisplayName("Checkout - Cart Empty - Throws BadRequestException")
    void checkout_whenCartIsEmpty_throwsBadRequestException() {
      mockAuthenticatedUser(testBuyer);
      when(addressRepository.findByIdAndUserId(any(), any()))
          .thenReturn(Optional.of(shippingAddress));
      when(cartItemRepository.findByUserId(testBuyer.getId())).thenReturn(Collections.emptyList());

      assertThrows(
          BadRequestException.class, () -> orderService.checkout(authentication, checkoutRequest));
    }

    @Test
    @DisplayName("Checkout - Stock Insufficient - Throws BadRequestException")
    void checkout_whenStockInsufficient_throwsBadRequestException() {
      mockAuthenticatedUser(testBuyer);
      product1.setStockQuantity(1);
      CartItem ci1 = new CartItem();
      ci1.setId(1L);
      ci1.setUser(testBuyer);
      ci1.setProduct(product1);
      ci1.setQuantity(2);

      when(addressRepository.findByIdAndUserId(any(), any()))
          .thenReturn(Optional.of(shippingAddress));
      when(cartItemRepository.findByUserId(testBuyer.getId())).thenReturn(List.of(ci1));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

      BadRequestException exception =
          assertThrows(
              BadRequestException.class,
              () -> orderService.checkout(authentication, checkoutRequest));
      assertTrue(exception.getMessage().contains("không đủ số lượng tồn kho"));
    }
  }

  // ... (Các nested class và test case khác giữ nguyên như phiên bản trước)
  @Nested
  @DisplayName("Get Orders Tests")
  class GetOrdersTests {
    @Test
    @DisplayName("Get My Orders As Buyer - Success")
    void getMyOrdersAsBuyer_success() {
      mockAuthenticatedUser(testBuyer);
      Pageable pageable = PageRequest.of(0, 10);
      Page<Order> orderPage = new PageImpl<>(List.of(orderEntity), pageable, 1);
      when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
      when(orderMapper.toOrderSummaryResponsePage(orderPage))
          .thenReturn(new PageImpl<>(List.of(orderSummaryResponseDto)));

      Page<OrderSummaryResponse> result =
          orderService.getMyOrdersAsBuyer(authentication, null, null, null, null, null, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Get My Orders As Farmer - Success")
    void getMyOrdersAsFarmer_success() {
      mockAuthenticatedUser(testFarmer);
      Pageable pageable = PageRequest.of(0, 10);
      Page<Order> orderPage = new PageImpl<>(List.of(orderEntity), pageable, 1);
      when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
      when(orderMapper.toOrderSummaryResponsePage(orderPage))
          .thenReturn(new PageImpl<>(List.of(orderSummaryResponseDto)));

      Page<OrderSummaryResponse> result =
          orderService.getMyOrdersAsFarmer(authentication, null, null, null, null, null, pageable);

      assertNotNull(result);
      assertEquals(1, result.getTotalElements());
    }
  }

  @Nested
  @DisplayName("Get Order Details Tests")
  class GetOrderDetailsTests {
    @Test
    @DisplayName("Get Order Details - By Buyer - Success")
    void getOrderDetails_byBuyer_success() {
      mockAuthenticatedUser(testBuyer);
      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
      when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

      OrderResponse result = orderService.getOrderDetails(authentication, orderEntity.getId());

      assertNotNull(result);
      assertEquals(orderResponseDto.getOrderCode(), result.getOrderCode());
      verify(fileStorageService).getFileUrl(eq("product1/image.jpg"));
    }

    @Test
    @DisplayName("Get Order Details - User Not Authorized")
    void getOrderDetails_whenUserNotAuthorized_shouldThrowAccessDenied() {
      User unauthorizedUser =
          User.builder()
              .id(99L)
              .email("other@example.com")
              .roles(Set.of(new Role(RoleType.ROLE_CONSUMER)))
              .build();
      mockAuthenticatedUser(unauthorizedUser);
      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

      assertThrows(
          AccessDeniedException.class,
          () -> orderService.getOrderDetails(authentication, orderEntity.getId()));
    }
  }

  @Nested
  @DisplayName("Update Order Status Tests")
  class UpdateOrderStatusTests {
    @Test
    @DisplayName("Update Order Status - By Farmer - Success")
    void updateOrderStatus_byFarmer_Success() {
      mockAuthenticatedUser(testFarmer);
      orderEntity.setStatus(OrderStatus.PENDING);
      orderEntity.setFarmer(testFarmer);
      OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
      request.setStatus(OrderStatus.CONFIRMED);

      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
      when(orderRepository.save(any(Order.class))).thenReturn(orderEntity);
      when(orderMapper.toOrderResponse(any(Order.class))).thenReturn(orderResponseDto);

      OrderResponse result =
          orderService.updateOrderStatus(authentication, orderEntity.getId(), request);

      assertNotNull(result);
      assertEquals(OrderStatus.CONFIRMED, orderEntity.getStatus());
      verify(notificationService)
          .sendOrderStatusUpdateNotification(eq(orderEntity), eq(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("Update Order Status - Invalid Transition")
    void updateOrderStatus_invalidTransition_throwsBadRequest() {
      mockAuthenticatedUser(testFarmer);
      orderEntity.setStatus(OrderStatus.DELIVERED);
      orderEntity.setFarmer(testFarmer);
      OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
      request.setStatus(OrderStatus.PROCESSING);

      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

      assertThrows(
          BadRequestException.class,
          () -> orderService.updateOrderStatus(authentication, orderEntity.getId(), request));
    }
  }

  @Nested
  @DisplayName("Cancel Order Tests")
  class CancelOrderTests {
    @Test
    @DisplayName("Cancel Order - By Buyer - Success")
    void cancelOrder_byBuyerWhenPending_shouldSucceedAndRestoreStock() {
      mockAuthenticatedUser(testBuyer);
      orderEntity.setStatus(OrderStatus.PENDING);
      orderEntity.setBuyer(testBuyer);
      product1.setStockQuantity(8);
      OrderItem oi = new OrderItem();
      oi.setProduct(product1);
      oi.setQuantity(2);
      orderEntity.setOrderItems(Set.of(oi));

      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
      when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
      when(productRepository.saveAndFlush(product1)).thenReturn(product1);
      when(orderRepository.save(any(Order.class))).thenReturn(orderEntity);
      when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

      OrderResponse result = orderService.cancelOrder(authentication, orderEntity.getId());

      assertNotNull(result);
      assertEquals(OrderStatus.CANCELLED, orderEntity.getStatus());
      assertEquals(10, product1.getStockQuantity());
      verify(notificationService).sendOrderCancellationNotification(orderEntity);
    }
  }

  @Nested
  @DisplayName("Payment URL Creation Tests")
  class PaymentUrlCreationTests {

    @Test
    @DisplayName("Create Payment URL - Order Already Paid")
    void createPaymentUrl_whenOrderAlreadyPaid_shouldThrowBadRequest() {
      mockAuthenticatedUser(testBuyer);
      orderEntity.setPaymentStatus(PaymentStatus.PAID);
      when(orderRepository.findByIdAndBuyerId(orderEntity.getId(), testBuyer.getId()))
          .thenReturn(Optional.of(orderEntity));

      assertThrows(
          BadRequestException.class,
          () ->
              orderService.createPaymentUrl(
                  authentication, orderEntity.getId(), PaymentMethod.VNPAY, httpServletRequest));
    }
  }

  @Nested
  @DisplayName("Bank Transfer Info Tests")
  class BankTransferInfoTests {
    @Test
    @DisplayName("Get Bank Transfer Info - Success")
    void getBankTransferInfoForOrder_success() {
      mockAuthenticatedUser(testBuyer);
      orderEntity.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
      orderEntity.setPaymentStatus(PaymentStatus.PENDING);

      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

      assertDoesNotThrow(
          () -> orderService.getBankTransferInfoForOrder(orderEntity.getId(), authentication));
    }

    @Test
    @DisplayName("Get Bank Transfer Info - Wrong Payment Method")
    void getBankTransferInfoForOrder_whenNotBankTransfer_shouldThrowBadRequest() {
      mockAuthenticatedUser(testBuyer);
      orderEntity.setPaymentMethod(PaymentMethod.COD);
      when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

      assertThrows(
          BadRequestException.class,
          () -> orderService.getBankTransferInfoForOrder(orderEntity.getId(), authentication));
    }
  }
}
