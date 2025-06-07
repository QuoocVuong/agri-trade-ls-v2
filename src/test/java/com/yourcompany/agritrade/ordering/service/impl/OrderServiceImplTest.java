package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.notification.service.NotificationService;
import com.yourcompany.agritrade.ordering.domain.*;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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


import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private InvoiceService invoiceService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private FileStorageService fileStorageService; // Thêm mock này
    @Mock @Qualifier("vnPayService") private PaymentGatewayService vnPayService;
    @Mock @Qualifier("moMoService") private PaymentGatewayService moMoService;
    @Mock private HttpServletRequest httpServletRequest; // Thêm mock này
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
    private OrderSummaryResponse orderSummaryResponseDto;


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
        farmerProfile.setProvinceCode("20");
        farmerProfile.setFarmName("Test Farm");
        testFarmer.setFarmerProfile(farmerProfile);

        product1 = Product.builder().id(10L).name("Sản phẩm A").price(new BigDecimal("100.00"))
                .stockQuantity(10).status(ProductStatus.PUBLISHED).farmer(testFarmer).unit("kg")
                .provinceCode("20").version(0L).images(new HashSet<>()).build();
        product2 = Product.builder().id(20L).name("Sản phẩm B").price(new BigDecimal("50.00"))
                .stockQuantity(5).status(ProductStatus.PUBLISHED).farmer(testFarmer).unit("cái")
                .provinceCode("20").version(0L).images(new HashSet<>()).build();

        // Thêm ảnh mẫu cho product1 để test populateProductImageUrlsInOrder
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

        orderEntity = new Order();
        orderEntity.setId(1L);
        orderEntity.setOrderCode("ORD-SAMPLE-001");
        orderEntity.setBuyer(testBuyer);
        orderEntity.setFarmer(testFarmer);
        orderEntity.setStatus(OrderStatus.PENDING);
        orderEntity.setPaymentMethod(PaymentMethod.COD);
        orderEntity.setPaymentStatus(PaymentStatus.PENDING);
        orderEntity.setTotalAmount(new BigDecimal("250.00")); // Giả sử
        orderEntity.setOrderItems(new HashSet<>()); // Khởi tạo
        OrderItem oi1 = new OrderItem(); oi1.setProduct(product1); oi1.setQuantity(2); oi1.setPricePerUnit(product1.getPrice()); oi1.setTotalPrice(product1.getPrice().multiply(BigDecimal.valueOf(2))); orderEntity.addOrderItem(oi1);


        orderResponseDto = new OrderResponse();
        orderResponseDto.setId(1L);
        orderResponseDto.setOrderCode("ORD-SAMPLE-001");
        // ... các trường khác của DTO

        orderSummaryResponseDto = new OrderSummaryResponse();
        orderSummaryResponseDto.setId(1L);
        orderSummaryResponseDto.setOrderCode("ORD-SAMPLE-001");
        // ...

        // Mock authentication chung, có thể override trong từng test nếu cần user khác
        lenient().when(authentication.getName()).thenReturn(testBuyer.getEmail());
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(userRepository.findByEmail(testBuyer.getEmail())).thenReturn(Optional.of(testBuyer));
        lenient().when(userRepository.findByEmail(testFarmer.getEmail())).thenReturn(Optional.of(testFarmer));
        lenient().when(userRepository.findByEmail(testAdmin.getEmail())).thenReturn(Optional.of(testAdmin));

        // Mock cho fileStorageService.getFileUrl
        lenient().when(fileStorageService.getFileUrl(anyString())).thenAnswer(invocation -> "mockedUrl/" + invocation.getArgument(0));
    }

    private void mockAuthenticatedUser(User user, RoleType roleType) {
        lenient().when(authentication.getPrincipal()).thenReturn(user); // Hoặc UserDetails nếu bạn dùng
        lenient().when(authentication.getName()).thenReturn(user.getEmail());
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        Collection<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority(roleType.name())); // Chỉ thêm role được truyền vào
        lenient().when(authentication.getAuthorities()).thenReturn((Collection) authorities);
    }


    @Nested
    @DisplayName("Get My Orders (Buyer)")
    class GetMyOrdersBuyer {
        @Test
        @DisplayName("Get My Orders As Buyer - With Keyword and Status")
        void getMyOrdersAsBuyer_withKeywordAndStatus_shouldReturnFilteredOrders() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            Pageable pageable = PageRequest.of(0, 10);
            String keyword = "ORD-SAMPLE";
            OrderStatus status = OrderStatus.PENDING;
            PaymentMethod paymentMethod = PaymentMethod.COD;
            PaymentStatus paymentStatus = PaymentStatus.PENDING;

            Page<Order> orderPage = new PageImpl<>(List.of(orderEntity), pageable, 1);
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
            when(orderMapper.toOrderSummaryResponsePage(orderPage)).thenReturn(new PageImpl<>(List.of(orderSummaryResponseDto)));

            Page<OrderSummaryResponse> result = orderService.getMyOrdersAsBuyer(authentication, keyword, status, paymentMethod, paymentStatus,  pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals(orderSummaryResponseDto.getOrderCode(), result.getContent().get(0).getOrderCode());
            verify(orderRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("Get My Orders (Farmer)")
    class GetMyOrdersFarmer {
        @Test
        @DisplayName("Get My Orders As Farmer - With Keyword and Status")
        void getMyOrdersAsFarmer_withKeywordAndStatus_shouldReturnFilteredOrders() {
            mockAuthenticatedUser(testFarmer, RoleType.ROLE_FARMER);
            Pageable pageable = PageRequest.of(0, 10);
            String keyword = "Test Buyer";
            OrderStatus status = OrderStatus.PENDING;
            PaymentMethod paymentMethod = PaymentMethod.COD;
            PaymentStatus paymentStatus = PaymentStatus.PENDING;

            Page<Order> orderPage = new PageImpl<>(List.of(orderEntity), pageable, 1);
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
            when(orderMapper.toOrderSummaryResponsePage(orderPage)).thenReturn(new PageImpl<>(List.of(orderSummaryResponseDto)));

            Page<OrderSummaryResponse> result = orderService.getMyOrdersAsFarmer(authentication, keyword, status, paymentMethod, paymentStatus, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(orderRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    @Nested
    @DisplayName("Get All Orders (Admin)")
    class GetAllOrdersAdmin {
        @Test
        @DisplayName("Get All Orders For Admin - With Filters")
        void getAllOrdersForAdmin_withFilters_shouldReturnFilteredOrders() {
            mockAuthenticatedUser(testAdmin, RoleType.ROLE_ADMIN);
            Pageable pageable = PageRequest.of(0, 10);
            String keyword = "ORD";
            OrderStatus status = OrderStatus.PROCESSING;
            Long buyerIdParam = testBuyer.getId();
            Long farmerIdParam = testFarmer.getId();
            PaymentMethod paymentMethod = PaymentMethod.COD;
            PaymentStatus paymentStatus = PaymentStatus.PENDING;

            Page<Order> orderPage = new PageImpl<>(List.of(orderEntity), pageable, 1);
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
            when(orderMapper.toOrderSummaryResponsePage(orderPage)).thenReturn(new PageImpl<>(List.of(orderSummaryResponseDto)));

            Page<OrderSummaryResponse> result = orderService.getAllOrdersForAdmin(keyword, status, paymentMethod, paymentStatus, buyerIdParam, farmerIdParam, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            verify(orderRepository).findAll(any(Specification.class), eq(pageable));
        }
    }


    @Nested
    @DisplayName("Get Order Details Tests")
    class GetOrderDetailsTests {
        @Test
        @DisplayName("Get Order Details - By Buyer - Success")
        void getOrderDetails_byBuyer_success() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

            OrderResponse result = orderService.getOrderDetails(authentication, orderEntity.getId());

            assertNotNull(result);
            assertEquals(orderResponseDto.getOrderCode(), result.getOrderCode());
            verify(fileStorageService, atLeastOnce()).getFileUrl(eq("product1/image.jpg")); // Kiểm tra populate được gọi
        }

        @Test
        @DisplayName("Get Order Details - By Farmer - Success")
        void getOrderDetails_byFarmer_success() {
            mockAuthenticatedUser(testFarmer, RoleType.ROLE_FARMER);
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

            OrderResponse result = orderService.getOrderDetails(authentication, orderEntity.getId());
            assertNotNull(result);
        }

        @Test
        @DisplayName("Get Order Details - By Admin - Success")
        void getOrderDetails_byAdmin_success() {
            mockAuthenticatedUser(testAdmin, RoleType.ROLE_ADMIN);
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

            OrderResponse result = orderService.getOrderDetails(authentication, orderEntity.getId());
            assertNotNull(result);
        }


        @Test
        @DisplayName("Get Order Details - Order Not Found")
        void getOrderDetails_whenOrderNotFound_shouldThrowResourceNotFound() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            when(orderRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderDetails(authentication, 99L));
        }

        @Test
        @DisplayName("Get Order Details - User Not Authorized (Not Buyer, Farmer, or Admin)")
        void getOrderDetails_whenUserNotAuthorized_shouldThrowAccessDenied() {
            User unauthorizedUser = User.builder().id(4L).email("other@example.com").roles(Set.of(new Role(RoleType.ROLE_CONSUMER))).build();
            mockAuthenticatedUser(unauthorizedUser, RoleType.ROLE_CONSUMER); // User này không phải buyer/farmer của orderEntity
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

            assertThrows(AccessDeniedException.class, () -> orderService.getOrderDetails(authentication, orderEntity.getId()));
        }

        @Test
        @DisplayName("Get Order Details By Code - Success")
        void getOrderDetailsByCode_success() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            when(orderRepository.findByOrderCodeWithDetails(orderEntity.getOrderCode())).thenReturn(Optional.of(orderEntity));
            when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

            OrderResponse result = orderService.getOrderDetailsByCode(authentication, orderEntity.getOrderCode());
            assertNotNull(result);
        }

        @Test
        @DisplayName("Get Order Details For Admin - Order Not Found")
        void getOrderDetailsForAdmin_whenOrderNotFound_shouldThrowResourceNotFound() {
            mockAuthenticatedUser(testAdmin, RoleType.ROLE_ADMIN);
            when(orderRepository.findByIdWithDetails(99L)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderDetailsForAdmin(99L));
        }
    }

    @Nested
    @DisplayName("Update Order Status Tests")
    class UpdateOrderStatusTests {
        // Các test case cho updateOrderStatus đã có trong file test trước,
        // bạn có thể di chuyển hoặc giữ lại và bổ sung nếu cần.
        // Ví dụ:
        @Test
        @DisplayName("Update Order Status - By Farmer - From PENDING to CONFIRMED - Success")
        void updateOrderStatus_byFarmer_PendingToConfirmed_Success() {
            mockAuthenticatedUser(testFarmer, RoleType.ROLE_FARMER);
            orderEntity.setStatus(OrderStatus.PENDING);
            orderEntity.setFarmer(testFarmer); // Đảm bảo order này của farmer

            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
            request.setStatus(OrderStatus.CONFIRMED);

            Order updatedOrder = new Order(); // Tạo đối tượng mới để mock kết quả save
            updatedOrder.setId(orderEntity.getId());
            updatedOrder.setStatus(OrderStatus.CONFIRMED);
            updatedOrder.setBuyer(testBuyer); // Cần cho getOrderDetailsForAdmin
            updatedOrder.setFarmer(testFarmer); // Cần cho getOrderDetailsForAdmin
            // ... các trường khác nếu toOrderResponse cần

            when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
            // Mock cho getOrderDetailsForAdmin được gọi ở cuối
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(updatedOrder));
            when(orderMapper.toOrderResponse(updatedOrder)).thenReturn(orderResponseDto); // Giả sử DTO này được trả về

            OrderResponse result = orderService.updateOrderStatus(authentication, orderEntity.getId(), request);

            assertNotNull(result);
            assertEquals(OrderStatus.CONFIRMED, orderEntity.getStatus()); // Kiểm tra entity đã thay đổi
            verify(notificationService).sendOrderStatusUpdateNotification(eq(updatedOrder), eq(OrderStatus.PENDING));
        }

        @Test
        @DisplayName("Update Order Status - Invalid Transition - Throws BadRequestException")
        void updateOrderStatus_invalidTransition_throwsBadRequest() {
            mockAuthenticatedUser(testFarmer, RoleType.ROLE_FARMER);
            orderEntity.setStatus(OrderStatus.DELIVERED); // Trạng thái cuối cùng
            orderEntity.setFarmer(testFarmer);
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
            request.setStatus(OrderStatus.PROCESSING);

            when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

            assertThrows(BadRequestException.class,
                    () -> orderService.updateOrderStatus(authentication, orderEntity.getId(), request));
        }
    }

    @Nested
    @DisplayName("Cancel Order Tests")
    class CancelOrderTests {
        // Các test case cho cancelOrder đã có trong file test trước.
        // Ví dụ:
        @Test
        @DisplayName("Cancel Order - By Buyer - When Order is PENDING - Success")
        void cancelOrder_byBuyerWhenPending_shouldSucceedAndRestoreStock() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            orderEntity.setStatus(OrderStatus.PENDING);
            orderEntity.setBuyer(testBuyer); // Đảm bảo order này của buyer
            product1.setStockQuantity(8); // Tồn kho sau khi đặt
            OrderItem oi = new OrderItem(); oi.setProduct(product1); oi.setQuantity(2);
            orderEntity.setOrderItems(Set.of(oi));


            when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderItemRepository.findByOrderId(orderEntity.getId())).thenReturn(new ArrayList<>(orderEntity.getOrderItems()));
            when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
            when(productRepository.saveAndFlush(product1)).thenReturn(product1);
            when(orderRepository.save(any(Order.class))).thenReturn(orderEntity);
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderMapper.toOrderResponse(orderEntity)).thenReturn(orderResponseDto);

            OrderResponse result = orderService.cancelOrder(authentication, orderEntity.getId());

            assertNotNull(result);
            assertEquals(OrderStatus.CANCELLED, orderEntity.getStatus());
            assertEquals(10, product1.getStockQuantity()); // 8 + 2
            verify(notificationService).sendOrderCancellationNotification(orderEntity);
        }
    }

    @Nested
    @DisplayName("Payment URL Creation Tests")
    class PaymentUrlCreationTests {
//        @Test
//        @DisplayName("Create Payment URL - VNPAY - Success")
//        void createPaymentUrl_forVnPay_success() {
//            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
//            orderEntity.setPaymentMethod(PaymentMethod.COD); // Giả sử ban đầu là COD để kích hoạt logic save
//            orderEntity.setPaymentStatus(PaymentStatus.PENDING);
//            PaymentUrlResponse expectedVnPayResponse = new PaymentUrlResponse("http://vnpay-url.com", "VNPAY");
//
//            when(orderRepository.findByIdAndBuyerId(orderEntity.getId(), testBuyer.getId())).thenReturn(Optional.of(orderEntity));
//            when(httpServletRequest.getHeader(anyString())).thenReturn("127.0.0.1"); // Mock IP address
//
//            // Mock orderRepository.save để trả về đối tượng đã được cập nhật
//            // Điều này quan trọng để đảm bảo đối tượng Order truyền vào vnPayService là đối tượng mới nhất
//            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
//                Order savedOrder = invocation.getArgument(0);
//                // Giả lập rằng ID không thay đổi, nhưng các thuộc tính khác có thể đã thay đổi
//                // (ví dụ: paymentMethod, paymentStatus)
//                return savedOrder;
//            });
//
//            // Sử dụng ArgumentCaptor cho Order được truyền vào vnPayService
//            ArgumentCaptor<Order> orderCaptorForVnPay = ArgumentCaptor.forClass(Order.class);
//            doReturn(expectedVnPayResponse)
//                    .when(vnPayService)
//                    .createVnPayPaymentUrl(orderCaptorForVnPay.capture(), anyString(), anyString());
//
//
//            // Act
//            PaymentUrlResponse result = orderService.createPaymentUrl(authentication, orderEntity.getId(), PaymentMethod.VNPAY, httpServletRequest);
//
//            // Assert
//            assertNotNull(result);
//            assertEquals(expectedVnPayResponse.getPaymentUrl(), result.getPaymentUrl());
//
//            Order capturedOrderForVnPay = orderCaptorForVnPay.getValue();
//            assertNotNull(capturedOrderForVnPay);
//            assertEquals(orderEntity.getId(), capturedOrderForVnPay.getId());
//            assertEquals(PaymentMethod.VNPAY, capturedOrderForVnPay.getPaymentMethod()); // Quan trọng: kiểm tra order đã được cập nhật
//            assertEquals(PaymentStatus.PENDING, capturedOrderForVnPay.getPaymentStatus());
//
//            // Verify orderRepository.save được gọi (ít nhất 1 lần do cập nhật paymentMethod)
//            verify(orderRepository, atLeastOnce()).save(any(Order.class));
//        }

        @Test
        @DisplayName("Create Payment URL - Order Already Paid - Throws BadRequestException")
        void createPaymentUrl_whenOrderAlreadyPaid_shouldThrowBadRequest() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            orderEntity.setPaymentStatus(PaymentStatus.PAID);
            when(orderRepository.findByIdAndBuyerId(orderEntity.getId(), testBuyer.getId())).thenReturn(Optional.of(orderEntity));

            assertThrows(BadRequestException.class,
                    () -> orderService.createPaymentUrl(authentication, orderEntity.getId(), PaymentMethod.VNPAY, httpServletRequest));
        }
    }

    @Nested
    @DisplayName("Bank Transfer Info Tests")
    class BankTransferInfoTests {
        @Test
        @DisplayName("Get Bank Transfer Info - Success")
        void getBankTransferInfoForOrder_success() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            orderEntity.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
            orderEntity.setPaymentStatus(PaymentStatus.PENDING);

            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            // Giả sử các @Value đã được inject vào orderService
            // Không cần mock chúng trực tiếp trong unit test này, trừ khi bạn muốn test các giá trị cụ thể

            BankTransferInfoResponse result = orderService.getBankTransferInfoForOrder(orderEntity.getId(), authentication);

            assertNotNull(result);
            assertEquals("CK " + orderEntity.getOrderCode(), result.getTransferContent());
            // Kiểm tra các trường khác nếu cần
        }

        @Test
        @DisplayName("Get Bank Transfer Info - Order Not Bank Transfer - Throws BadRequestException")
        void getBankTransferInfoForOrder_whenNotBankTransfer_shouldThrowBadRequest() {
            mockAuthenticatedUser(testBuyer, RoleType.ROLE_CONSUMER);
            orderEntity.setPaymentMethod(PaymentMethod.COD); // Không phải BANK_TRANSFER
            when(orderRepository.findByIdWithDetails(orderEntity.getId())).thenReturn(Optional.of(orderEntity));

            assertThrows(BadRequestException.class,
                    () -> orderService.getBankTransferInfoForOrder(orderEntity.getId(), authentication));
        }
    }

    @Nested
    @DisplayName("Admin Payment Confirmation Tests")
    class AdminPaymentConfirmationTests {
        @Test
        @DisplayName("Confirm Bank Transfer Payment - Success")
        void confirmBankTransferPayment_success() {
            mockAuthenticatedUser(testAdmin, RoleType.ROLE_ADMIN); // Giả sử admin thực hiện
            orderEntity.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
            orderEntity.setPaymentStatus(PaymentStatus.PENDING);
            orderEntity.setStatus(OrderStatus.PENDING); // Hoặc AWAITING_PAYMENT
            String bankTxCode = "BANK_TX_123";

            Order savedOrder = new Order(); // Tạo đối tượng mới để mock kết quả save
            // Sao chép các thuộc tính cần thiết từ orderEntity sang savedOrder
            // và cập nhật các trạng thái mong đợi sau khi xác nhận
            savedOrder.setId(orderEntity.getId());
            savedOrder.setOrderCode(orderEntity.getOrderCode());
            savedOrder.setPaymentStatus(PaymentStatus.PAID);
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            savedOrder.setBuyer(testBuyer); // Cần cho notification
            // ...

            when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto); // Giả sử DTO này được trả về

            OrderResponse result = orderService.confirmBankTransferPayment(orderEntity.getId(), bankTxCode);

            assertNotNull(result);
            assertEquals(PaymentStatus.PAID, orderEntity.getPaymentStatus());
            assertEquals(OrderStatus.CONFIRMED, orderEntity.getStatus());
            verify(paymentRepository).save(argThat(p -> p.getTransactionCode().equals(bankTxCode) && p.getStatus() == PaymentTransactionStatus.SUCCESS));
            verify(notificationService).sendPaymentSuccessNotification(savedOrder);
        }

        @Test
        @DisplayName("Confirm Order Payment By Admin - For Invoice - Success")
        void confirmOrderPaymentByAdmin_forInvoice_success() {
            mockAuthenticatedUser(testAdmin, RoleType.ROLE_ADMIN);
            orderEntity.setPaymentMethod(PaymentMethod.INVOICE);
            orderEntity.setPaymentStatus(PaymentStatus.AWAITING_PAYMENT_TERM);
            orderEntity.setStatus(OrderStatus.DELIVERED); // Giả sử đơn hàng công nợ đã giao

            Invoice invoice = new Invoice();
            invoice.setId(50L);
            invoice.setOrder(orderEntity);
            invoice.setStatus(InvoiceStatus.ISSUED);
            invoice.setInvoiceNumber("INV-" + orderEntity.getOrderCode());

            Order savedOrder = new Order();
            savedOrder.setId(orderEntity.getId());
            savedOrder.setPaymentStatus(PaymentStatus.PAID);
            savedOrder.setStatus(OrderStatus.DELIVERED); // Trạng thái đơn hàng không đổi
            savedOrder.setBuyer(testBuyer);
            // ...

            when(orderRepository.findById(orderEntity.getId())).thenReturn(Optional.of(orderEntity));
            when(invoiceRepository.findByOrderId(orderEntity.getId())).thenReturn(Optional.of(invoice));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(invoiceRepository.save(any(Invoice.class))).thenReturn(invoice);
            when(orderMapper.toOrderResponse(savedOrder)).thenReturn(orderResponseDto);

            OrderResponse result = orderService.confirmOrderPaymentByAdmin(orderEntity.getId(), PaymentMethod.BANK_TRANSFER, "ADMIN_CONFIRM_TX", "Admin confirmed");

            assertNotNull(result);
            assertEquals(PaymentStatus.PAID, orderEntity.getPaymentStatus());
            assertEquals(InvoiceStatus.PAID, invoice.getStatus());
            verify(notificationService).sendPaymentSuccessNotification(savedOrder);
        }
    }

    // TODO: Thêm test cho calculateOrderTotals với các kịch bản khác nhau (địa chỉ, loại đơn hàng)
    // TODO: Test cho các trường hợp lỗi của createPaymentUrl (ví dụ: payment gateway service ném lỗi)
    // TODO: Test cho populateProductImageUrlsInOrder (kiểm tra imageUrl được set đúng)
}