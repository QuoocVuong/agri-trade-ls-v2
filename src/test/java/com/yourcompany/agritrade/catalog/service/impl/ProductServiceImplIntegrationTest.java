package com.yourcompany.agritrade.catalog.service.impl;

import com.github.slugify.Slugify;
import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus; // Import ProductStatus
import com.yourcompany.agritrade.catalog.dto.request.ProductRequest;
import com.yourcompany.agritrade.catalog.dto.response.ProductDetailResponse; // Import ProductDetailResponse
import com.yourcompany.agritrade.catalog.repository.CategoryRepository;
import com.yourcompany.agritrade.catalog.repository.ProductRepository;
import com.yourcompany.agritrade.catalog.service.ProductService;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User; // Import User
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository; // Import UserRepository
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import com.yourcompany.agritrade.common.model.RoleType; // Import RoleType

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional // Rollback transactions after each test
public class ProductServiceImplIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository; // Inject UserRepository

    @Autowired
    private RoleRepository roleRepository; // Inject RoleRepository

    private User testFarmer;
    private Authentication farmerAuthentication;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false"); // Disable Flyway for tests if schema is managed by Hibernate
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create"); // Ensure schema is created and dropped
    }

    @BeforeEach
    void setUp() {
        // Clear context before each test to avoid interference
        SecurityContextHolder.clearContext();

        // Create a FARMER role if it doesn't exist (or ensure it exists)
        Role farmerRole = roleRepository.findByName(RoleType.ROLE_FARMER)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(RoleType.ROLE_FARMER);
                    return roleRepository.save(newRole);
                });

        // Create a test farmer user
        testFarmer = new User();
        testFarmer.setEmail("farmer@example.com");
        testFarmer.setPasswordHash("hashedpassword"); // Not used for auth in this test directly
        testFarmer.setFullName("Test Farmer");
        testFarmer.setActive(true);
        Set<Role> roles = new HashSet<>();
        roles.add(farmerRole);
        testFarmer.setRoles(roles);
        testFarmer = userRepository.save(testFarmer);

        // Create Authentication object for the test farmer
        farmerAuthentication = new UsernamePasswordAuthenticationToken(
                testFarmer.getEmail(), // Principal can be email or UserDetails object
                null,
                Collections.singletonList(new SimpleGrantedAuthority(RoleType.ROLE_FARMER.name()))
        );
        // Set the authentication in the security context
        SecurityContextHolder.getContext().setAuthentication(farmerAuthentication);
    }

    private Category createAndSaveTestCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setDescription("Description for " + name);
        Slugify slg = Slugify.builder().transliterator(true).build(); // Thêm transliterator để xử lý tiếng Việt tốt hơn
        String slug = slg.slugify(name);

        category.setSlug(slug);
        return categoryRepository.save(category);

    }

    @Test
    void testCreateMyProduct_success() {
        Category savedCategory = createAndSaveTestCategory("Fruits");

        ProductRequest createRequest = new ProductRequest();
        createRequest.setName("Test Apple");
        createRequest.setDescription("Fresh red apples");
        createRequest.setPrice(BigDecimal.valueOf(10.0));
        createRequest.setUnit("Kg");
        createRequest.setStockQuantity(100);
        createRequest.setCategoryId(savedCategory.getId());
        // Set other required fields for ProductRequest if any

        ProductDetailResponse createdProductResponse = productService.createMyProduct(farmerAuthentication, createRequest);
        assertNotNull(createdProductResponse);
        assertNotNull(createdProductResponse.getId());

        Optional<Product> foundProductOpt = productRepository.findById(createdProductResponse.getId());
        assertTrue(foundProductOpt.isPresent());
        Product foundProduct = foundProductOpt.get();

        assertEquals("Test Apple", foundProduct.getName());
        assertEquals(0, BigDecimal.valueOf(10.0).compareTo(foundProduct.getPrice())); // Compare BigDecimal
        assertNotNull(foundProduct.getCategory());
        assertEquals(savedCategory.getId(), foundProduct.getCategory().getId());
        assertEquals(testFarmer.getId(), foundProduct.getFarmer().getId()); // Check if farmer is set
        assertEquals(ProductStatus.PENDING_APPROVAL, foundProduct.getStatus()); // Check default status
    }

    @Test
    void testGetMyProductById_success() {
        Category savedCategory = createAndSaveTestCategory("Vegetables");

        // Create a product for the testFarmer
        Product product = new Product();
        product.setName("Carrot");
        product.setPrice(BigDecimal.valueOf(5.0));
        product.setCategory(savedCategory);
        product.setFarmer(testFarmer); // Associate with the farmer
        product.setStatus(ProductStatus.PUBLISHED); // Assume it's published for this test
        Product savedProduct = productRepository.save(product);

        ProductDetailResponse foundProductResponse = productService.getMyProductById(farmerAuthentication, savedProduct.getId());

        assertNotNull(foundProductResponse);
        assertEquals(savedProduct.getId(), foundProductResponse.getId());
        assertEquals("Carrot", foundProductResponse.getName());
    }

    @Test
    void testUpdateMyProduct_success() {
        Category savedCategory = createAndSaveTestCategory("Dairy");
        Category anotherCategory = createAndSaveTestCategory("Beverages");


        Product product = new Product();
        product.setName("Milk");
        product.setPrice(BigDecimal.valueOf(3.0));
        product.setCategory(savedCategory);
        product.setFarmer(testFarmer);
        product.setStatus(ProductStatus.PUBLISHED);
        Product savedProduct = productRepository.save(product);

        ProductRequest updateRequest = new ProductRequest();
        updateRequest.setName("Almond Milk");
        updateRequest.setDescription("Updated description");
        updateRequest.setPrice(BigDecimal.valueOf(3.5));
        updateRequest.setUnit("Liter");
        updateRequest.setStockQuantity(50);
        updateRequest.setCategoryId(anotherCategory.getId()); // Change category

        ProductDetailResponse updatedProductResponse = productService.updateMyProduct(farmerAuthentication, savedProduct.getId(), updateRequest);

        assertNotNull(updatedProductResponse);
        assertEquals("Almond Milk", updatedProductResponse.getName());
        assertEquals(0, BigDecimal.valueOf(3.5).compareTo(updatedProductResponse.getPrice()));
        assertEquals(anotherCategory.getId(), updatedProductResponse.getCategory().getId());

        // Verify in DB
        Product updatedProductFromDb = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertEquals("Almond Milk", updatedProductFromDb.getName());
        assertEquals(anotherCategory.getId(), updatedProductFromDb.getCategory().getId());
    }

    @Test
    void testDeleteMyProduct_success() {
        Category savedCategory = createAndSaveTestCategory("Bakery");

        Product product = new Product();
        product.setName("Bread");
        product.setPrice(BigDecimal.valueOf(2.0));
        product.setCategory(savedCategory);
        product.setFarmer(testFarmer);
        product.setStatus(ProductStatus.PUBLISHED);
        Product savedProduct = productRepository.save(product);

        assertTrue(productRepository.findById(savedProduct.getId()).isPresent());

        productService.deleteMyProduct(farmerAuthentication, savedProduct.getId());

        // If soft delete (@Where(clause = "is_deleted = false")), findById should return empty
        assertFalse(productRepository.findById(savedProduct.getId()).isPresent());

        // Optional: Verify soft delete flag if you have a method to find including deleted
        // Product deletedProduct = productRepository.findByIdIncludingDeleted(savedProduct.getId()).orElseThrow();
        // assertTrue(deletedProduct.isDeleted());
    }

    // Test for getPublicProductBySlug (Example)
    @Test
    void testGetPublicProductBySlug_success() {
        Category category = createAndSaveTestCategory("Public Category");
        Product product = new Product();
        product.setName("Public Product Name");
        product.setSlug("public-product-name"); // Ensure slug is set and unique
        product.setDescription("Public description");
        product.setPrice(BigDecimal.valueOf(100.00));
        product.setCategory(category);
        product.setFarmer(testFarmer); // A farmer must own the product
        product.setStatus(ProductStatus.PUBLISHED); // Must be published to be public
        productRepository.save(product);

        ProductDetailResponse foundProduct = productService.getPublicProductBySlug("public-product-name");
        assertNotNull(foundProduct);
        assertEquals("Public Product Name", foundProduct.getName());
    }




    // You had two testDeleteProduct_success methods, I'm removing the duplicate.
    // Ensure each test method has a unique name.
}