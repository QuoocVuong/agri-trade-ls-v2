package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.OutOfStockException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.mapper.OrderMapper;
import com.yourcompany.agritrade.ordering.repository.*;
import com.yourcompany.agritrade.ordering.service.InvoiceService;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private FarmerProfileRepository farmerProfileRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private NotificationService notificationService;
    @Mock private InvoiceService invoiceService; // Mock InvoiceService
    @Mock private Authentication authentication;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User testBuyer, testFarmer, testAdmin;
    private Product product1, product2;
    private Address shippingAddress;
    private FarmerProfile farmerProfile;
    private CheckoutRequest checkoutRequest;
    private Order orderEntity;
    private OrderResponse orderResponseDto;



    @BeforeEach
    void setUp() {
        testBuyer = User.builder().id(1L).email("buyer@example.com").fullName("Test Buyer")
                .roles(Set.of(new Role(RoleType.ROLE_CONSUMER))).build();
        testFarmer = User.builder().id(2L).email("farmer@example.com").fullName("Test Farmer")
                .roles(Set.of(new Role(RoleType.ROLE_FARMER))).build();
        testAdmin = User.builder().id(3L).email("admin@example.com").fullName("Test Admin")
                .roles(Set.of(new Role(RoleType.ROLE_ADMIN))).build();


        farmerProfile = new FarmerProfile();
        farmerProfile.setUserId(testFarmer.getId());
        farmerProfile.setUser(testFarmer);
        farmerProfile.setProvinceCode("20"); // Lạng Sơn
        farmerProfile.setFarmName("Test Farm");
        testFarmer.setFarmerProfile(farmerProfile); // Liên kết ngược

        product1 = Product.builder().id(10L).name("Sản phẩm A").price(new BigDecimal("100.00"))
                .stockQuantity(10).status(ProductStatus.PUBLISHED).farmer(testFarmer).unit("kg")
                .provinceCode("20").version(0L).build(); // Thêm version
        product2 = Product.builder().id(20L).name("Sản phẩm B").price(new BigDecimal("50.00"))
                .stockQuantity(5).status(ProductStatus.PUBLISHED).farmer(testFarmer).unit("cái")
                .provinceCode("20").version(0L).build();

        shippingAddress = new Address();
        shippingAddress.setId(100L);
        shippingAddress.setUser(testBuyer);
        shippingAddress.setFullName("Recipient Name");
        shippingAddress.setPhoneNumber("0123456789");
        shippingAddress.setAddressDetail("123 Main St");
        shippingAddress.setProvinceCode("20"); // Lạng Sơn
        shippingAddress.setDistrictCode("180");
        shippingAddress.setWardCode("06289");


        checkoutRequest = new CheckoutRequest();
        checkoutRequest.setShippingAddressId(shippingAddress.getId());
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);
        checkoutRequest.setNotes("Test order notes");

        // Khởi tạo orderEntity mẫu cho các test cần đến nó (ví dụ: cancelOrder, updateStatus)
        orderEntity = new Order();
        orderEntity.setId(1L); // ID giả lập
        orderEntity.setOrderCode("ORD-SAMPLE-001");
        orderEntity.setBuyer(testBuyer);
        orderEntity.setFarmer(testFarmer);
        // ... (set các trường cần thiết khác cho orderEntity)

        orderResponseDto = new OrderResponse(); // DTO này sẽ được tùy chỉnh trong từng test
        orderResponseDto.setId(1L);
        orderResponseDto.setOrderCode("ORD-SAMPLE-001");


        lenient().when(authentication.getName()).thenAnswer(inv -> {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) { // UserDetails của Spring Security
                return ((UserDetails) principal).getUsername();
            } else if (principal instanceof User) { // Nếu principal là đối tượng User của bạn
                return ((User) principal).getEmail();
            } else if (principal instanceof String) {
                return (String) principal;
            }
            // Fallback nếu không xác định được
            if (authentication.getPrincipal() == testBuyer) return testBuyer.getEmail();
            if (authentication.getPrincipal() == testFarmer) return testFarmer.getEmail();
            if (authentication.getPrincipal() == testAdmin) return testAdmin.getEmail();
            return "unknown@example.com";
        });
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
    }

    private void mockAuthentication(User user, RoleType roleType) {
        // Khi mock getPrincipal, trả về đối tượng User của bạn
        lenient().when(authentication.getPrincipal()).thenReturn(user);
        lenient().when(authentication.getName()).thenReturn(user.getEmail());

        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }



    @Test
    @DisplayName("Checkout - Success with Single Farmer and COD")
    void checkout_whenValidCartAndCOD_shouldCreateOrderAndClearCart() {
        // Arrange
        mockAuthentication(testBuyer, RoleType.ROLE_CONSUMER);

        CartItem cartItem1 = new CartItem(); cartItem1.setProduct(product1); cartItem1.setQuantity(2);
        CartItem cartItem2 = new CartItem(); cartItem2.setProduct(product2); cartItem2.setQuantity(1);
        List<CartItem> cartItems = List.of(cartItem1, cartItem2);

        when(addressRepository.findByIdAndUserId(shippingAddress.getId(), testBuyer.getId())).thenReturn(Optional.of(shippingAddress));
        when(cartItemRepository.findByUserId(testBuyer.getId())).thenReturn(cartItems);
        when(userRepository.findById(testFarmer.getId())).thenReturn(Optional.of(testFarmer)); // Farmer của sản phẩm
        when(farmerProfileRepository.findById(testFarmer.getId())).thenReturn(Optional.of(farmerProfile));

        // Mock productRepository.findById cho từng sản phẩm để kiểm tra tồn kho
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));

        // Mock orderRepository.save()
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L); // Gán ID giả lập
            o.setCreatedAt(LocalDateTime.now());
            o.setUpdatedAt(LocalDateTime.now());
            // Giả lập orderItems được thêm vào
            if (o.getOrderItems().isEmpty()) {
                OrderItem oi1 = new OrderItem(); oi1.setProduct(product1); oi1.setQuantity(2); oi1.setPricePerUnit(product1.getPrice()); oi1.setTotalPrice(product1.getPrice().multiply(BigDecimal.valueOf(2))); o.addOrderItem(oi1);
                OrderItem oi2 = new OrderItem(); oi2.setProduct(product2); oi2.setQuantity(1); oi2.setPricePerUnit(product2.getPrice()); oi2.setTotalPrice(product2.getPrice().multiply(BigDecimal.valueOf(1))); o.addOrderItem(oi2);
            }
            return o;
        });
        // Mock paymentRepository.save()
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock orderMapper
        when(orderMapper.toOrderResponse(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            OrderResponse dto = new OrderResponse(); dto.setId(o.getId()); /* ... map các trường khác ... */ return dto;
        });
        // Mock findByIdWithDetails cho việc reload
        when(orderRepository.findByIdWithDetails(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Order o = new Order(); o.setId(id); /* ... cấu hình order đầy đủ ... */ return Optional.of(o);
        });


        // Act
        List<OrderResponse> result = orderService.checkout(authentication, checkoutRequest);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(result.get(0).getId());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertEquals(testBuyer, savedOrder.getBuyer());
        assertEquals(testFarmer, savedOrder.getFarmer());
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertEquals(PaymentMethod.COD, savedOrder.getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, savedOrder.getPaymentStatus());
        assertEquals(2, savedOrder.getOrderItems().size());

        // Kiểm tra tồn kho đã giảm
        assertEquals(8, product1.getStockQuantity()); // 10 - 2
        assertEquals(4, product2.getStockQuantity()); // 5 - 1

        verify(cartItemRepository).deleteAllById(anyList());
        verify(notificationService, times(2)).sendOrderPlacementNotification(any(Order.class));
        verify(paymentRepository).save(any(Payment.class)); // Kiểm tra payment record được tạo
        verify(invoiceService, never()).getOrCreateInvoiceForOrder(any(Order.class)); // Không tạo invoice cho COD
    }

    @Test
    @DisplayName("Checkout - Product Out Of Stock - Throws BadRequestException with unavailable message")
    void checkout_whenProductOutOfStock_shouldThrowBadRequestWithUnavailableMessage() { // Đổi tên test
        // Arrange
        mockAuthentication(testBuyer, RoleType.ROLE_CONSUMER);
        product1.setStockQuantity(1); // Chỉ còn 1
        CartItem cartItem1 = new CartItem();
        cartItem1.setId(500L); // Gán ID cho cartItem để log không báo null
        cartItem1.setProduct(product1);
        cartItem1.setQuantity(2); // Yêu cầu 2
        List<CartItem> cartItems = List.of(cartItem1);

        when(addressRepository.findByIdAndUserId(shippingAddress.getId(), testBuyer.getId())).thenReturn(Optional.of(shippingAddress));
        when(cartItemRepository.findByUserId(testBuyer.getId())).thenReturn(cartItems);
        when(userRepository.findById(testFarmer.getId())).thenReturn(Optional.of(testFarmer));
        when(farmerProfileRepository.findById(testFarmer.getId())).thenReturn(Optional.of(farmerProfile));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> { // Mong đợi BadRequestException
            orderService.checkout(authentication, checkoutRequest);
        });
        // Kiểm tra message của BadRequestException
        assertTrue(exception.getMessage().contains("Sản phẩm '" + product1.getName() + "' không còn khả dụng"));

        verify(orderRepository, never()).save(any(Order.class));
        // Kiểm tra cartItemRepository.deleteAllById được gọi với ID của item lỗi
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartItemRepository).deleteAllById(captor.capture());
        assertTrue(captor.getValue().contains(cartItem1.getId()));
    }

    @Test
    @DisplayName("Cancel Order - By Buyer - Success")
    void cancelOrder_byBuyerWhenPending_shouldCancelOrderAndRestoreStock() {
        // Arrange
        mockAuthentication(testBuyer, RoleType.ROLE_CONSUMER);
        orderEntity.setBuyer(testBuyer); // Đảm bảo buyer là người đang cancel
        orderEntity.setStatus(OrderStatus.PENDING);
        product1.setStockQuantity(8); // Giả sử tồn kho sau khi đặt hàng

        OrderItem oi = new OrderItem(); oi.setProduct(product1); oi.setQuantity(2);
        orderEntity.setOrderItems(Set.of(oi));


        when(userRepository.findByEmail(testBuyer.getEmail())).thenReturn(Optional.of(testBuyer));
        when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
        when(orderItemRepository.findByOrderId(orderEntity.getId())).thenReturn(new ArrayList<>(orderEntity.getOrderItems()));
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(orderRepository.save(any(Order.class))).thenReturn(orderEntity); // Trả về order đã cập nhật
        when(productRepository.saveAndFlush(any(Product.class))).thenReturn(product1);
        // Mock cho findByIdWithDetails sau khi cancel
        when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
        when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto); // Giả sử mapper trả về DTO này


        // Act
        OrderResponse result = orderService.cancelOrder(authentication, orderEntity.getId());

        // Assert
        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, orderEntity.getStatus());
        assertEquals(PaymentStatus.FAILED, orderEntity.getPaymentStatus()); // Vì đang PENDING
        assertEquals(10, product1.getStockQuantity()); // 8 + 2

        verify(orderRepository).save(orderEntity);
        verify(productRepository).saveAndFlush(product1);
        verify(notificationService).sendOrderCancellationNotification(orderEntity);
    }

    @Test
    @DisplayName("Cancel Order - By Buyer - Fails if Order Not Cancellable")
    void cancelOrder_byBuyerWhenNotCancellableStatus_shouldThrowBadRequestException() {
        // Arrange
        mockAuthentication(testBuyer, RoleType.ROLE_CONSUMER);
        orderEntity.setBuyer(testBuyer);
        orderEntity.setStatus(OrderStatus.SHIPPING); // Không thể hủy

        when(userRepository.findByEmail(testBuyer.getEmail())).thenReturn(Optional.of(testBuyer));
        when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            orderService.cancelOrder(authentication, orderEntity.getId());
        });
        assertTrue(exception.getMessage().contains("Order cannot be cancelled in its current status"));
        verify(orderRepository, never()).save(any(Order.class));
    }


    @Test
    @DisplayName("Update Order Status - By Farmer - Valid Transition")
    void updateOrderStatus_byFarmerWithValidTransition_shouldUpdateStatusAndNotify() {
        // Arrange
        mockAuthentication(testFarmer, RoleType.ROLE_FARMER);
        orderEntity.setFarmer(testFarmer); // Đảm bảo farmer là người cập nhật
        orderEntity.setStatus(OrderStatus.PENDING);
        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.CONFIRMED);

        Order updatedOrderEntity = new Order(); // Tạo một đối tượng mới để mô phỏng kết quả sau khi save
        updatedOrderEntity.setId(orderEntity.getId());
        updatedOrderEntity.setStatus(OrderStatus.CONFIRMED);
        // ... sao chép các trường khác nếu cần cho toOrderResponse ...
        updatedOrderEntity.setBuyer(testBuyer); // Cần cho toOrderResponse
        updatedOrderEntity.setFarmer(testFarmer); // Cần cho toOrderResponse


        when(userRepository.findByEmail(testFarmer.getEmail())).thenReturn(Optional.of(testFarmer));
        when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrderEntity);
        // Mock findByIdWithDetails để trả về updatedOrderEntity cho getOrderDetailsForAdmin
        when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(updatedOrderEntity));
        when(orderMapper.toOrderResponse(updatedOrderEntity)).thenReturn(orderResponseDto); // Giả sử DTO này được trả về


        // Act
        OrderResponse result = orderService.updateOrderStatus(authentication, orderEntity.getId(), request);

        // Assert
        assertNotNull(result);
        // assertEquals(OrderStatus.CONFIRMED, result.getStatus()); // Kiểm tra DTO trả về
        assertEquals(OrderStatus.CONFIRMED, orderEntity.getStatus()); // Kiểm tra entity đã thay đổi

        verify(orderRepository).save(orderEntity);
        verify(notificationService).sendOrderStatusUpdateNotification(eq(updatedOrderEntity), eq(OrderStatus.PENDING));
    }

    @Test
    @DisplayName("Update Order Status - By Admin - Delivered COD - Updates Payment Status")
    void updateOrderStatus_byAdminToDeliveredForCOD_shouldUpdatePaymentStatusToPaid() {
        // Arrange
        mockAuthentication(testAdmin, RoleType.ROLE_ADMIN);
        orderEntity.setStatus(OrderStatus.SHIPPING);
        orderEntity.setPaymentMethod(PaymentMethod.COD);
        orderEntity.setPaymentStatus(PaymentStatus.PENDING);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setStatus(OrderStatus.DELIVERED);

        Order deliveredOrderEntity = new Order();
        deliveredOrderEntity.setId(orderEntity.getId());
        deliveredOrderEntity.setStatus(OrderStatus.DELIVERED);
        deliveredOrderEntity.setPaymentStatus(PaymentStatus.PAID); // Service sẽ set
        deliveredOrderEntity.setPaymentMethod(PaymentMethod.COD);
        // ...
        deliveredOrderEntity.setBuyer(testBuyer);
        deliveredOrderEntity.setFarmer(testFarmer);


        when(userRepository.findByEmail(testAdmin.getEmail())).thenReturn(Optional.of(testAdmin));
        when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
        when(orderRepository.save(any(Order.class))).thenReturn(deliveredOrderEntity);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0)); // Mock save payment
        when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(deliveredOrderEntity));
        when(orderMapper.toOrderResponse(deliveredOrderEntity)).thenReturn(orderResponseDto);


        // Act
        orderService.updateOrderStatus(authentication, orderEntity.getId(), request);

        // Assert
        assertEquals(OrderStatus.DELIVERED, orderEntity.getStatus());
        assertEquals(PaymentStatus.PAID, orderEntity.getPaymentStatus());

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertEquals(PaymentMethod.COD.name(), savedPayment.getPaymentGateway());
        assertEquals(PaymentTransactionStatus.SUCCESS, savedPayment.getStatus());

        verify(notificationService).sendOrderStatusUpdateNotification(eq(deliveredOrderEntity), eq(OrderStatus.SHIPPING));
    }


    // TODO: Thêm các Unit Test cho:
    // - checkout với nhiều farmer -> tạo nhiều order
    // - checkout với sản phẩm B2B và logic giá B2B
    // - checkout với lỗi OptimisticLock (khó unit test, dễ hơn với integration test)
    // - updateOrderStatus với các chuyển đổi trạng thái không hợp lệ
    // - updateOrderStatus bởi user không có quyền
    // - cancelOrder bởi Admin
    // - cancelOrder khi đơn hàng đã thanh toán (paymentStatus -> REFUNDED)
    // - getMyOrdersAsBuyer, getMyOrdersAsFarmer, getAllOrdersForAdmin (kiểm tra Specification và Pageable)
    // - getOrderDetails, getOrderDetailsByCode (kiểm tra quyền truy cập)
    // - calculateOrderTotals (với các kịch bản shipping, discount khác nhau)
    // - confirmBankTransferPayment, confirmOrderPaymentByAdmin
    // - getBankTransferInfoForOrder
}