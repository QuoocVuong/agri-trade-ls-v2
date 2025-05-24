package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.ordering.domain.OrderType;
import com.yourcompany.agritrade.ordering.domain.PaymentMethod;
import com.yourcompany.agritrade.ordering.domain.PaymentStatus;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional // Rollback transactions after each test
public class OrderRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository; // Cần để tạo Role

    private User testBuyer;
    private User testFarmer;

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
        // Xóa theo thứ tự để tránh lỗi khóa ngoại
        orderRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();


        Role buyerRole = roleRepository.findByName(RoleType.ROLE_CONSUMER)
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
        testBuyer.setEmail("buyer.order.repo@example.com");
        testBuyer.setPasswordHash("password");
        testBuyer.setFullName("Test Buyer OrderRepo");
        testBuyer.setPhoneNumber("1234567890"); // Thêm phone number nếu unique
        testBuyer.setActive(true);
        testBuyer.setRoles(Collections.singleton(buyerRole));
        testBuyer = userRepository.save(testBuyer);

        testFarmer = new User();
        testFarmer.setEmail("farmer.order.repo@example.com");
        testFarmer.setPasswordHash("password");
        testFarmer.setFullName("Test Farmer OrderRepo");
        testFarmer.setPhoneNumber("0987654321"); // Thêm phone number nếu unique
        testFarmer.setActive(true);
        testFarmer.setRoles(Collections.singleton(farmerRole));
        testFarmer = userRepository.save(testFarmer);
    }

    private Order createTestOrder(User buyer, User farmer, String orderCode) {
        Order order = new Order();
        order.setBuyer(buyer);
        order.setFarmer(farmer);
        order.setOrderCode(orderCode);
        order.setOrderType(OrderType.B2C); // Hoặc B2B tùy test case
        order.setShippingFullName("Test Shipping Name");
        order.setShippingPhoneNumber("1231231234");
        order.setShippingAddressDetail("123 Test Street");
        order.setShippingProvinceCode("20");
        order.setShippingDistrictCode("180");
        order.setShippingWardCode("06289");
        order.setSubTotal(new BigDecimal("100.00"));
        order.setShippingFee(new BigDecimal("10.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("110.00"));
        order.setPaymentMethod(PaymentMethod.COD);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setStatus(OrderStatus.PENDING);
        return orderRepository.save(order);
    }

    @Test
    void testSaveAndFindByIdWithDetails() {
        Order savedOrder = createTestOrder(testBuyer, testFarmer, "ORDER-001");

        Optional<Order> foundOrderOpt = orderRepository.findByIdWithDetails(savedOrder.getId());

        assertTrue(foundOrderOpt.isPresent());
        Order foundOrder = foundOrderOpt.get();
        assertEquals(savedOrder.getId(), foundOrder.getId());
        assertEquals(testBuyer.getId(), foundOrder.getBuyer().getId());
        assertEquals(testFarmer.getId(), foundOrder.getFarmer().getId());
        assertEquals("ORDER-001", foundOrder.getOrderCode());
    }

    @Test
    void testFindByBuyerIdWithDetails() {
        createTestOrder(testBuyer, testFarmer, "BUYER-ORDER-001");
        createTestOrder(testBuyer, testFarmer, "BUYER-ORDER-002");

        User anotherBuyer = new User();
        anotherBuyer.setEmail("anotherbuyer@example.com");
        anotherBuyer.setPasswordHash("pw");
        anotherBuyer.setFullName("Another Buyer");
        anotherBuyer.setPhoneNumber("1122334455");
        anotherBuyer.setActive(true);
        anotherBuyer.setRoles(testBuyer.getRoles()); // Dùng chung role cho đơn giản
        userRepository.save(anotherBuyer);
        createTestOrder(anotherBuyer, testFarmer, "OTHER-BUYER-ORDER-001");

        Page<Order> buyerOrdersPage = orderRepository.findByBuyerIdWithDetails(testBuyer.getId(), PageRequest.of(0, 10));

        assertNotNull(buyerOrdersPage);
        assertEquals(2, buyerOrdersPage.getTotalElements());
        assertTrue(buyerOrdersPage.getContent().stream().allMatch(o -> o.getBuyer().getId().equals(testBuyer.getId())));
    }

    @Test
    void testFindByFarmerIdWithDetails() {
        createTestOrder(testBuyer, testFarmer, "FARMER-ORDER-001");
        createTestOrder(testBuyer, testFarmer, "FARMER-ORDER-002");

        User anotherFarmer = new User();
        anotherFarmer.setEmail("anotherfarmer@example.com");
        anotherFarmer.setPasswordHash("pw");
        anotherFarmer.setFullName("Another Farmer");
        anotherFarmer.setPhoneNumber("2233445566");
        anotherFarmer.setActive(true);
        anotherFarmer.setRoles(testFarmer.getRoles());
        userRepository.save(anotherFarmer);
        createTestOrder(testBuyer, anotherFarmer, "OTHER-FARMER-ORDER-001");


        Page<Order> farmerOrdersPage = orderRepository.findByFarmerIdWithDetails(testFarmer.getId(), PageRequest.of(0, 10));

        assertNotNull(farmerOrdersPage);
        assertEquals(2, farmerOrdersPage.getTotalElements());
        assertTrue(farmerOrdersPage.getContent().stream().allMatch(o -> o.getFarmer().getId().equals(testFarmer.getId())));
    }
}