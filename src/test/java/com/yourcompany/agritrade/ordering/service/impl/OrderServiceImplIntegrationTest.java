package com.yourcompany.agritrade.ordering.service.impl;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.ordering.domain.*; // Import OrderStatus, OrderType, PaymentMethod, etc.
import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest; // Giả sử bạn dùng DTO này để tạo OrderItem
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.dto.response.OrderResponse;
import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.ordering.repository.OrderItemRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
import com.yourcompany.agritrade.ordering.service.OrderService;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.FarmerProfileRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional // Rollback transactions after each test
public class OrderServiceImplIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CartItemRepository cartItemRepository; // Cần để mô phỏng giỏ hàng
    @Autowired private FarmerProfileRepository farmerProfileRepository;


    private User testBuyer;
    private User testFarmer;
    private Authentication buyerAuthentication;
    private Authentication farmerAuthentication;
    private Category defaultCategory;
    private Address testShippingAddress;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll(); // Xóa cart items
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        addressRepository.deleteAll();
        farmerProfileRepository.deleteAll();
        userRepository.deleteAll(); // Xóa user trước role để tránh lỗi khóa ngoại
        roleRepository.deleteAll();


        Role buyerRole = roleRepository.findByName(RoleType.ROLE_CONSUMER).orElseGet(() -> roleRepository.save(new Role(null, RoleType.ROLE_CONSUMER, "Consumer Role", null)));
        Role farmerRole = roleRepository.findByName(RoleType.ROLE_FARMER).orElseGet(() -> roleRepository.save(new Role(null, RoleType.ROLE_FARMER, "Farmer Role", null)));

        testBuyer = new User();
        testBuyer.setEmail("buyer@example.com");
        testBuyer.setPasswordHash("password"); // Dùng setPasswordHash
        testBuyer.setFullName("Buyer Test User"); // Dùng setFullName
        testBuyer.setActive(true);
        Set<Role> buyerRolesSet = new HashSet<>();
        buyerRolesSet.add(buyerRole);
        testBuyer.setRoles(buyerRolesSet); // << SỬA Ở ĐÂY
        testBuyer = userRepository.save(testBuyer);

        testFarmer = new User();
        testFarmer.setEmail("farmer@example.com");
        testFarmer.setPasswordHash("password");
        testFarmer.setFullName("Farmer Test User");
        testFarmer.setActive(true);
        Set<Role> farmerRolesSet = new HashSet<>();
        farmerRolesSet.add(farmerRole);
        testFarmer.setRoles(farmerRolesSet); // << SỬA Ở ĐÂY
        testFarmer = userRepository.save(testFarmer);

        // Tạo FarmerProfile cho testFarmer
        FarmerProfile farmerProfile = new FarmerProfile();
        farmerProfile.setUser(testFarmer);
        farmerProfile.setUserId(testFarmer.getId());
        farmerProfile.setFarmName("Test Farm");
        farmerProfile.setProvinceCode("20"); // Lạng Sơn
        farmerProfile.setVerificationStatus(com.yourcompany.agritrade.common.model.VerificationStatus.VERIFIED);
        farmerProfileRepository.save(farmerProfile);
        testFarmer.setFarmerProfile(farmerProfile); // Liên kết ngược lại nếu cần
        userRepository.save(testFarmer); // Lưu lại user với profile


        buyerAuthentication = new UsernamePasswordAuthenticationToken(testBuyer.getEmail(), null, Collections.singletonList(new SimpleGrantedAuthority(RoleType.ROLE_CONSUMER.name())));
        farmerAuthentication = new UsernamePasswordAuthenticationToken(testFarmer.getEmail(), null, Collections.singletonList(new SimpleGrantedAuthority(RoleType.ROLE_FARMER.name())));

        defaultCategory = new Category();
        defaultCategory.setName("Default Category");
        defaultCategory.setSlug("default-category");
        defaultCategory = categoryRepository.save(defaultCategory);

        testShippingAddress = new Address();
        testShippingAddress.setUser(testBuyer);
        testShippingAddress.setFullName("Test Buyer Address");
        testShippingAddress.setPhoneNumber("0123456789");
        testShippingAddress.setAddressDetail("123 Test St");
        testShippingAddress.setProvinceCode("20"); // Lạng Sơn
        testShippingAddress.setDistrictCode("180"); // Huyện Cao Lộc (ví dụ)
        testShippingAddress.setWardCode("06289");   // Xã Công Sơn (ví dụ)
        testShippingAddress.setDefault(true);
        testShippingAddress = addressRepository.save(testShippingAddress);
    }

    private Product createAndSaveProduct(String name, BigDecimal price, User farmer, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("Description for " + name);
        product.setPrice(price);
        product.setCategory(defaultCategory);
        product.setFarmer(farmer);
        product.setUnit("Kg");
        product.setStockQuantity(stock);
        product.setStatus(ProductStatus.PUBLISHED); // Mặc định là published để checkout
        product.setProvinceCode(farmer.getFarmerProfile().getProvinceCode()); // Lấy tỉnh từ farmer profile
        return productRepository.save(product);
    }

    @Test
    void testCheckout_success() {
        SecurityContextHolder.getContext().setAuthentication(buyerAuthentication);

        Product product1 = createAndSaveProduct("Apple", BigDecimal.valueOf(10.0), testFarmer, 100);
        Product product2 = createAndSaveProduct("Banana", BigDecimal.valueOf(20.0), testFarmer, 50);

        // Tạo CartItems cho buyer
        CartItem cartItem1 = new CartItem();
        cartItem1.setUser(testBuyer);
        cartItem1.setProduct(product1);
        cartItem1.setQuantity(2);
        cartItemRepository.save(cartItem1);

        CartItem cartItem2 = new CartItem();
        cartItem2.setUser(testBuyer);
        cartItem2.setProduct(product2);
        cartItem2.setQuantity(1);
        cartItemRepository.save(cartItem2);

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setShippingAddressId(testShippingAddress.getId());
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);
        checkoutRequest.setNotes("Test checkout notes");

        List<OrderResponse> createdOrdersResponse = orderService.checkout(buyerAuthentication, checkoutRequest);

        assertNotNull(createdOrdersResponse);
        assertEquals(1, createdOrdersResponse.size()); // Vì cả 2 sản phẩm đều từ 1 farmer
        OrderResponse orderResponse = createdOrdersResponse.get(0);
        assertNotNull(orderResponse.getId());

        Optional<Order> orderOpt = orderRepository.findByIdWithDetails(orderResponse.getId());
        assertTrue(orderOpt.isPresent());
        Order createdOrder = orderOpt.get();

        assertEquals(testBuyer.getId(), createdOrder.getBuyer().getId());
        assertEquals(testFarmer.getId(), createdOrder.getFarmer().getId());
        assertEquals(testShippingAddress.getAddressDetail(), createdOrder.getShippingAddressDetail());
        assertEquals(OrderStatus.PENDING, createdOrder.getStatus());
        assertEquals(PaymentStatus.PENDING, createdOrder.getPaymentStatus());
        assertEquals(PaymentMethod.COD, createdOrder.getPaymentMethod());

        assertNotNull(createdOrder.getOrderItems());
        assertEquals(2, createdOrder.getOrderItems().size());

        BigDecimal expectedSubtotal = (BigDecimal.valueOf(10.0).multiply(BigDecimal.valueOf(2)))
                .add(BigDecimal.valueOf(20.0).multiply(BigDecimal.valueOf(1)));
        assertEquals(0, expectedSubtotal.compareTo(createdOrder.getSubTotal()));

        // Giả sử phí ship nội tỉnh Lạng Sơn là 15000
        BigDecimal expectedShippingFee = new BigDecimal("15000.00");
        assertEquals(0, expectedShippingFee.compareTo(createdOrder.getShippingFee()));

        // Giả sử không có discount trong test này
        BigDecimal expectedDiscount = BigDecimal.ZERO;
        assertEquals(0, expectedDiscount.compareTo(createdOrder.getDiscountAmount()));

        BigDecimal expectedTotalAmount = expectedSubtotal.add(expectedShippingFee).subtract(expectedDiscount);
        assertEquals(0, expectedTotalAmount.compareTo(createdOrder.getTotalAmount()));

        // Kiểm tra cart đã được xóa
        assertEquals(0, cartItemRepository.findByUserId(testBuyer.getId()).size());
    }

    @Test
    void testGetMyOrdersAsBuyer_success() {
        SecurityContextHolder.getContext().setAuthentication(buyerAuthentication);
        // Tạo một vài đơn hàng cho testBuyer
        // Order 1
        Order order1 = new Order();
        order1.setBuyer(testBuyer);
        order1.setFarmer(testFarmer);
        order1.setOrderCode(orderService.generateOrderCode()); // Sử dụng service để tạo mã
        order1.setOrderType(OrderType.B2C);
        order1.setShippingFullName("Test Buyer");
        order1.setShippingPhoneNumber("0123456789");
        order1.setShippingAddressDetail("123 Main St");
        order1.setShippingProvinceCode("20");
        order1.setShippingDistrictCode("180");
        order1.setShippingWardCode("06289");
        order1.setSubTotal(BigDecimal.valueOf(100.0));
        order1.setShippingFee(BigDecimal.valueOf(15.0));
        order1.setTotalAmount(BigDecimal.valueOf(115.0));
        order1.setPaymentMethod(PaymentMethod.COD);
        order1.setStatus(OrderStatus.DELIVERED);
        order1.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order1);

        Pageable pageable = PageRequest.of(0, 10);
        Page<OrderSummaryResponse> resultPage = orderService.getMyOrdersAsBuyer(buyerAuthentication, pageable);

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements());
        assertEquals(testBuyer.getFullName(), resultPage.getContent().get(0).getBuyerName());
    }


    @Test
    void testUpdateOrderStatus_byFarmer_success() {
        SecurityContextHolder.getContext().setAuthentication(farmerAuthentication);

        Order order = new Order();
        // ... (tạo order với buyer, farmer là testFarmer, status PENDING) ...
        order.setBuyer(testBuyer);
        order.setFarmer(testFarmer);
        order.setOrderCode(orderService.generateOrderCode());
        order.setOrderType(OrderType.B2C);
        order.setShippingFullName("Test Buyer");
        order.setShippingPhoneNumber("0123456789");
        order.setShippingAddressDetail("123 Main St");
        order.setShippingProvinceCode("20");
        order.setShippingDistrictCode("180");
        order.setShippingWardCode("06289");
        order.setSubTotal(BigDecimal.valueOf(50.0));
        order.setShippingFee(BigDecimal.valueOf(10.0));
        order.setTotalAmount(BigDecimal.valueOf(60.0));
        order.setPaymentMethod(PaymentMethod.COD);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        Order savedOrder = orderRepository.save(order);

        OrderStatusUpdateRequest updateRequest = new OrderStatusUpdateRequest();
        updateRequest.setStatus(OrderStatus.CONFIRMED);

        OrderResponse updatedOrderResponse = orderService.updateOrderStatus(farmerAuthentication, savedOrder.getId(), updateRequest);

        assertNotNull(updatedOrderResponse);
        assertEquals(OrderStatus.CONFIRMED, updatedOrderResponse.getStatus());

        Order orderFromDb = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, orderFromDb.getStatus());
    }

    // TODO: Thêm các test cases khác:
    // - testCheckout_productOutOfStock
    // - testCheckout_invalidShippingAddress
    // - testGetOrderDetails_success_forBuyer
    // - testGetOrderDetails_success_forFarmer
    // - testGetOrderDetails_unauthorized
    // - testCancelOrder_byBuyer_success
    // - testCancelOrder_byAdmin_success
    // - testCancelOrder_invalidStatus
    // - testUpdateOrderStatus_byAdmin_success
    // - testUpdateOrderStatus_invalidTransition
    // - testConfirmBankTransferPayment_success (Admin)
    // - testConfirmOrderPaymentByAdmin_forInvoice_success (Admin)
}