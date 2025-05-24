package com.yourcompany.agritrade.catalog.repository;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductImage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional // Rollback transactions after each test
public class ProductImageRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository; // Needed to create a Product

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void testSaveAndFindByProduct() {
        // Create and save a Category
        Category category = new Category();
        category.setName("Test Category");
        categoryRepository.save(category);

        // Create and save a Product
        Product product = new Product();
        product.setName("Product with Images");
        product.setCategory(category);
        Product savedProduct = productRepository.save(product);

        // Create and save ProductImage entities
        ProductImage image1 = new ProductImage();
        image1.setImageUrl("http://example.com/image1.jpg");
        image1.setProduct(savedProduct);


        ProductImage image2 = new ProductImage();
        image2.setImageUrl("http://example.com/image2.png");
        image2.setProduct(savedProduct);


        productImageRepository.save(image1);
        productImageRepository.save(image2);




    }
}