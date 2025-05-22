package com.yourcompany.agritrade; // Hoặc package test utils

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest // QUAN TRỌNG
@Testcontainers
@ActiveProfiles("test")
@EntityScan(basePackages = { // <<<< THÊM ANNOTATION NÀY
        "com.yourcompany.agritrade.usermanagement.domain",
        "com.yourcompany.agritrade.catalog.domain", // Nếu test có liên quan
        "com.yourcompany.agritrade.ordering.domain",  // Nếu test có liên quan
        // ... thêm các package chứa entity mà test này cần ...
})
public abstract class AbstractDataJpaTestBase {

    @Container
    static final MySQLContainer<?> mySQLContainerForDataJpa =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("test_datajpa_db")
                    .withUsername("testuser")
                    .withPassword("testpass")
                    .withReuse(true);

    @DynamicPropertySource
    static void dataJpaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainerForDataJpa::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainerForDataJpa::getUsername);
        registry.add("spring.datasource.password", mySQLContainerForDataJpa::getPassword);
        // Cho @DataJpaTest, Hibernate quản lý schema, Flyway tắt
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }
}