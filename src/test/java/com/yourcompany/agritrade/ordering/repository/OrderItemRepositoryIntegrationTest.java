package com.yourcompany.agritrade.ordering.repository;

import com.github.slugify.Slugify;
import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.ordering.domain.*; // Import OrderStatus, OrderType, PaymentMethod, etc.
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional // Rollback transactions after each test
public class OrderItemRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CategoryRepository categoryRepository;


    private User testBuyer;
    private User testFarmer;
    private Order testOrder;
    private Product testProduct1;
    private Product testProduct2;
    private Category defaultCategory;


    @DynamicPropertySource // Annotation này nên ở cấp class hoặc static method
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
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
        testBuyer.setEmail("buyer.item.repo@example.com");
        testBuyer.setPasswordHash("password");
        testBuyer.setFullName("Buyer ItemRepo");
        testBuyer.setPhoneNumber("3334445555");
        testBuyer.setActive(true);
        Set<Role> buyerRolesSet = new HashSet<>();
        buyerRolesSet.add(buyerRole);
        testBuyer.setRoles(buyerRolesSet); // << SỬA Ở ĐÂY
        testBuyer = userRepository.save(testBuyer);

        testFarmer = new User();
        testFarmer.setEmail("farmer.item.repo@example.com");
        testFarmer.setPasswordHash("password");
        testFarmer.setFullName("Farmer ItemRepo");
        testFarmer.setPhoneNumber("6667778888");
        testFarmer.setActive(true);
        Set<Role> farmerRolesSet = new HashSet<>();
        farmerRolesSet.add(farmerRole);
        testFarmer.setRoles(farmerRolesSet); // << SỬA Ở ĐÂY
        testFarmer = userRepository.save(testFarmer);

        defaultCategory = new Category();
        defaultCategory.setName("Default Category for Item Test");
        defaultCategory.setSlug("default-category-item-test");
        defaultCategory = categoryRepository.save(defaultCategory);

        Slugify slg = Slugify.builder().transliterator(true).build(); // Khởi tạo Slugify


        testProduct1 = new Product();
        testProduct1.setName("Item Product 1");
        testProduct1.setPrice(BigDecimal.valueOf(100.0));
        testProduct1.setCategory(defaultCategory);
        testProduct1.setFarmer(testFarmer);
        testProduct1.setUnit("Cái");
        testProduct1.setStockQuantity(10);
        testProduct1.setStatus(ProductStatus.PUBLISHED);
        testProduct1.setProvinceCode("20");
        // Để đảm bảo slug duy nhất trong test, có thể thêm một phần ngẫu nhiên
        testProduct1.setSlug(slg.slugify(testProduct1.getName() + "-" + UUID.randomUUID().toString().substring(0, 6)));
        testProduct1 = productRepository.save(testProduct1);

        testProduct2 = new Product();
        testProduct2.setName("Item Product 2");
        testProduct2.setPrice(BigDecimal.valueOf(200.0));
        testProduct2.setCategory(defaultCategory);
        testProduct2.setFarmer(testFarmer);
        testProduct2.setUnit("Kg");
        testProduct2.setStockQuantity(20);
        testProduct2.setStatus(ProductStatus.PUBLISHED);
        testProduct2.setProvinceCode("20");
        // Tạo và set slug cho testProduct2
        testProduct2.setSlug(slg.slugify(testProduct2.getName() + "-" + UUID.randomUUID().toString().substring(0, 6)));
        testProduct2 = productRepository.save(testProduct2);

        testOrder = new Order();
        testOrder.setBuyer(testBuyer);
        testOrder.setFarmer(testFarmer);
        testOrder.setOrderCode("ORDERITEM-TEST-001");
        testOrder.setOrderType(OrderType.B2C);
        testOrder.setShippingFullName("Test Shipping");
        testOrder.setShippingPhoneNumber("1231231234");
        testOrder.setShippingAddressDetail("123 Test St");
        testOrder.setShippingProvinceCode("20");
        testOrder.setShippingDistrictCode("180");
        testOrder.setShippingWardCode("06289");
        testOrder.setSubTotal(BigDecimal.ZERO); // Sẽ được tính sau
        testOrder.setTotalAmount(BigDecimal.ZERO); // Sẽ được tính sau
        testOrder.setPaymentMethod(PaymentMethod.COD);
        testOrder.setPaymentStatus(PaymentStatus.PENDING);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder = orderRepository.save(testOrder);
    }


    @Test
    void testSaveAndFindByOrderId() {
        OrderItem item1 = new OrderItem();
        item1.setOrder(testOrder);
        item1.setProduct(testProduct1);
        item1.setProductName(testProduct1.getName()); // Sao chép thông tin
        item1.setUnit(testProduct1.getUnit());
        item1.setQuantity(2);
        item1.setPricePerUnit(testProduct1.getPrice()); // Giá tại thời điểm mua
        item1.setTotalPrice(testProduct1.getPrice().multiply(BigDecimal.valueOf(2)));
        orderItemRepository.save(item1);

        OrderItem item2 = new OrderItem();
        item2.setOrder(testOrder);
        item2.setProduct(testProduct2);
        item2.setProductName(testProduct2.getName());
        item2.setUnit(testProduct2.getUnit());
        item2.setQuantity(1);
        item2.setPricePerUnit(testProduct2.getPrice());
        item2.setTotalPrice(testProduct2.getPrice().multiply(BigDecimal.valueOf(1)));
        orderItemRepository.save(item2);

        List<OrderItem> foundItems = orderItemRepository.findByOrderId(testOrder.getId());

        assertNotNull(foundItems);
        assertEquals(2, foundItems.size());

        assertTrue(foundItems.stream().anyMatch(item ->
                item.getProduct().getId().equals(testProduct1.getId()) &&
                        item.getQuantity() == 2 &&
                        item.getPricePerUnit().compareTo(BigDecimal.valueOf(100.0)) == 0 // So sánh BigDecimal
        ));
        assertTrue(foundItems.stream().anyMatch(item ->
                item.getProduct().getId().equals(testProduct2.getId()) &&
                        item.getQuantity() == 1 &&
                        item.getPricePerUnit().compareTo(BigDecimal.valueOf(200.0)) == 0
        ));
    }
}