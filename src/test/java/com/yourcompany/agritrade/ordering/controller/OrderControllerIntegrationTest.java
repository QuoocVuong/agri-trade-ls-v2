package com.yourcompany.agritrade.ordering.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.ordering.domain.*; // Order, OrderItem, OrderStatus, OrderType, PaymentMethod, PaymentStatus
import com.yourcompany.agritrade.ordering.dto.request.CartItemRequest; // Giả định DTO này
import com.yourcompany.agritrade.ordering.dto.request.CheckoutRequest;
import com.yourcompany.agritrade.ordering.dto.request.OrderStatusUpdateRequest;
import com.yourcompany.agritrade.ordering.repository.CartItemRepository;
import com.yourcompany.agritrade.ordering.repository.OrderRepository;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AgriTradeApplication.class)
@AutoConfigureMockMvc // Quan trọng cho MockMvc
@Testcontainers
@Transactional
public class OrderControllerIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private FarmerProfileRepository farmerProfileRepository;
    @Autowired
    private CartItemRepository cartItemRepository;


    private User testBuyer;
    private User testFarmer;
    private Product testProduct1;
    private Product testProduct2;
    private Authentication buyerAuthentication;
    private Authentication farmerAuthentication;
    private Address testShippingAddress;
    private Category defaultCategory;

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
        orderRepository.deleteAll(); // Xóa order trước vì có khóa ngoại đến user, product
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        addressRepository.deleteAll();
        farmerProfileRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        cartItemRepository.deleteAll();


        Role consumerRole = roleRepository.findByName(RoleType.ROLE_CONSUMER)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(RoleType.ROLE_CONSUMER);
                    return roleRepository.save(newRole);
                });

        Role farmerRole = roleRepository.findByName(RoleType.ROLE_FARMER)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(RoleType.ROLE_FARMER);
                    return roleRepository.save(newRole);
                });

        testBuyer = new User();
        testBuyer.setEmail("buyer.controller@example.com");
        testBuyer.setPasswordHash("encodedPassword");
        testBuyer.setFullName("Buyer Controller");
        testBuyer.setPhoneNumber("0900000001");
        testBuyer.setActive(true);
        Set<Role> buyerRolesSet = new HashSet<>();
        buyerRolesSet.add(consumerRole);
        testBuyer.setRoles(buyerRolesSet); // << SỬA Ở ĐÂY
        testBuyer = userRepository.save(testBuyer);

        testFarmer = new User();
        testFarmer.setEmail("farmer.controller@example.com");
        testFarmer.setPasswordHash("encodedPassword");
        testFarmer.setFullName("Farmer Controller");
        testFarmer.setPhoneNumber("0900000002");
        testFarmer.setActive(true);
        Set<Role> farmerRolesSet = new HashSet<>();
        farmerRolesSet.add(farmerRole);
        testFarmer.setRoles(farmerRolesSet); // << SỬA Ở ĐÂY
        testFarmer = userRepository.save(testFarmer);

        FarmerProfile farmerProfile = new FarmerProfile();
        farmerProfile.setUser(testFarmer);
        farmerProfile.setUserId(testFarmer.getId());
        farmerProfile.setFarmName("Farmer Controller Farm");
        farmerProfile.setProvinceCode("20");
        farmerProfile.setVerificationStatus(com.yourcompany.agritrade.common.model.VerificationStatus.VERIFIED);
        //farmerProfileRepository.save(farmerProfile);

        testFarmer.setFarmerProfile(farmerProfile);

        testFarmer = userRepository.save(testFarmer);


        defaultCategory = new Category();
        defaultCategory.setName("Default Test Category");
        defaultCategory.setSlug("default-test-category");
        defaultCategory = categoryRepository.save(defaultCategory);

        testProduct1 = createProduct("Controller Product 1", BigDecimal.valueOf(10.0), testFarmer, 100);
        testProduct2 = createProduct("Controller Product 2", BigDecimal.valueOf(20.0), testFarmer, 50);

        testShippingAddress = new Address();
        testShippingAddress.setUser(testBuyer);
        testShippingAddress.setFullName("Test Buyer Address");
        testShippingAddress.setPhoneNumber("0123456789");
        testShippingAddress.setAddressDetail("123 Test St, Controller Test");
        testShippingAddress.setProvinceCode("20");
        testShippingAddress.setDistrictCode("180");
        testShippingAddress.setWardCode("06289");
        testShippingAddress.setDefault(true);
        testShippingAddress = addressRepository.save(testShippingAddress);

        buyerAuthentication = new UsernamePasswordAuthenticationToken(testBuyer.getEmail(), "password", Collections.singletonList(new SimpleGrantedAuthority(RoleType.ROLE_CONSUMER.name())));
        farmerAuthentication = new UsernamePasswordAuthenticationToken(testFarmer.getEmail(), "password", Collections.singletonList(new SimpleGrantedAuthority(RoleType.ROLE_FARMER.name())));
    }

    private Product createProduct(String name, BigDecimal price, User farmer, int stock) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setCategory(defaultCategory);
        product.setFarmer(farmer);
        product.setUnit("Kg");
        product.setStockQuantity(stock);
        product.setStatus(ProductStatus.PUBLISHED);
        product.setSlug(name.toLowerCase().replace(" ", "-") + UUID.randomUUID().toString().substring(0,4));
        product.setProvinceCode(farmer.getFarmerProfile() != null ? farmer.getFarmerProfile().getProvinceCode() : "20");
        return productRepository.save(product);
    }

    @Test
    void testCheckout_success() throws Exception {
        // Giả lập giỏ hàng bằng cách tạo CartItemRequest
        CartItemRequest itemReq1 = new CartItemRequest();
        itemReq1.setProductId(testProduct1.getId());
        itemReq1.setQuantity(2);

        CartItemRequest itemReq2 = new CartItemRequest();
        itemReq2.setProductId(testProduct2.getId());
        itemReq2.setQuantity(1);

        // Tạo CheckoutRequest
        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setShippingAddressId(testShippingAddress.getId());
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);
        checkoutRequest.setNotes("Test checkout from controller");
        // checkoutRequest.setItems(List.of(itemReq1, itemReq2)); // CheckoutRequest của bạn không có items, nó lấy từ CartService

        // Để test checkout, bạn cần mô phỏng CartItem trong DB cho testBuyer
        // Hoặc sửa CheckoutRequest để nhận List<CartItemRequest> và OrderService xử lý nó
        // Hiện tại, tôi sẽ giả định CartService sẽ được gọi và có item.
        // Để đơn giản, test này sẽ cần bạn đảm bảo CartService trả về đúng item cho testBuyer
        // Hoặc bạn mock CartService. Cách tốt hơn là tạo CartItem thực sự.

        // Tạo CartItem thực sự cho testBuyer
        com.yourcompany.agritrade.ordering.domain.CartItem cartItem1 = new com.yourcompany.agritrade.ordering.domain.CartItem();
        cartItem1.setUser(testBuyer);
        cartItem1.setProduct(testProduct1);
        cartItem1.setQuantity(2);
         cartItemRepository.save(cartItem1); // Cần CartItemRepository

        com.yourcompany.agritrade.ordering.domain.CartItem cartItem2 = new com.yourcompany.agritrade.ordering.domain.CartItem();
        cartItem2.setUser(testBuyer);
        cartItem2.setProduct(testProduct2);
        cartItem2.setQuantity(1);
         cartItemRepository.save(cartItem2);

        // Nếu bạn không inject CartItemRepository, bạn cần mock CartService
        // hoặc đảm bảo logic checkout không phụ thuộc trực tiếp vào CartItemRepository
        // mà là thông qua CartService được mock.
        // Vì đây là Integration Test, tốt nhất là tạo dữ liệu thật.
        // Bỏ qua phần save cart item nếu bạn không inject CartItemRepository ở đây.
        // Test này sẽ phụ thuộc vào việc OrderService.checkout lấy đúng cart items.

        mockMvc.perform(post("/api/orders/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(buyerAuthentication)) // Cung cấp Authentication
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutRequest)))
                .andExpect(status().isCreated()) // Mong đợi 201 Created
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1)) // Vì cùng farmer
                .andExpect(jsonPath("$.data[0].buyer.id").value(testBuyer.getId()))
                .andExpect(jsonPath("$.data[0].farmer.farmerId").value(testFarmer.getId()))
                .andExpect(jsonPath("$.data[0].orderItems.length()").value(2));
    }

    @Test
    void testGetMyOrderDetailsById_success() throws Exception {
        Order order = new Order();
        order.setBuyer(testBuyer);
        order.setFarmer(testFarmer);
        order.setOrderCode("CTRL-ORDER-001");
        order.setOrderType(OrderType.B2C);
        order.setShippingFullName("Test Recipient");
        order.setShippingPhoneNumber("0987654321");
        order.setShippingAddressDetail("123 Get St");
        order.setShippingProvinceCode("20");
        order.setShippingDistrictCode("180");
        order.setShippingWardCode("06289");
        order.setSubTotal(new BigDecimal("220.00"));
        order.setShippingFee(new BigDecimal("15.00"));
        order.setTotalAmount(new BigDecimal("235.00"));
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStatus(OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);

        mockMvc.perform(get("/api/orders/" + savedOrder.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(buyerAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(savedOrder.getId()))
                .andExpect(jsonPath("$.data.buyer.id").value(testBuyer.getId()));
    }

    // TODO: Thêm các test cho:
    // - getMyOrdersAsBuyer (với Pageable)
    // - getMyOrderDetailsByCode
    // - cancelMyOrder
    // - calculateOrderTotals
    // - createPaymentUrl
    // - getBankTransferInfo
    // - Các trường hợp lỗi (không tìm thấy, không có quyền, dữ liệu không hợp lệ)
}