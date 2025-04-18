package com.yourcompany.agritradels;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // Chạy trên port ngẫu nhiên
@ActiveProfiles("test") // Sử dụng application-test.yaml
@Testcontainers // Kích hoạt Testcontainers extension cho JUnit 5
public abstract class AbstractIntegrationTest {

    // Định nghĩa container MySQL
    // 'mysql:8.0' là tên image Docker
    // static để container chỉ khởi động 1 lần cho tất cả test trong các lớp kế thừa
    @Container
    static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb") // Tên DB trong container
            .withUsername("testuser")   // User trong container
            .withPassword("testpass");  // Password trong container
    // .withReuse(true); // Có thể bật để tái sử dụng container giữa các lần chạy test (nhanh hơn)

    // Cung cấp động các thuộc tính datasource cho Spring Boot
    // Các thuộc tính này sẽ ghi đè lên application-test.yaml (nếu có)
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl); // Lấy URL động từ container
        registry.add("spring.datasource.username", mySQLContainer::getUsername); // Lấy username động
        registry.add("spring.datasource.password", mySQLContainer::getPassword); // Lấy password động
        // registry.add("spring.flyway.url", mySQLContainer::getJdbcUrl); // Cấu hình cả cho Flyway nếu dùng
        // registry.add("spring.flyway.user", mySQLContainer::getUsername);
        // registry.add("spring.flyway.password", mySQLContainer::getPassword);
    }

    // Các phương thức hoặc cấu hình chung khác cho integration test có thể thêm ở đây
}