package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.AddressType;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional
public class AddressRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb_address_repo")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository; // Thêm RoleRepository

    private Role defaultRole;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        defaultRole = roleRepository.findByName(RoleType.ROLE_CONSUMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_CONSUMER)));
    }

    private User createAndSaveUser(String email, String phoneNumber) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("password");
        user.setFullName("Test User " + email);
        user.setPhoneNumber(phoneNumber);
        user.setActive(true);
        Set<Role> roles = new HashSet<>();
        roles.add(defaultRole);
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    private Address createAddress(User user, String addressDetail, boolean isDefault, AddressType type) {
        Address address = new Address();
        address.setUser(user);
        address.setFullName(user.getFullName()); // Hoặc một tên người nhận riêng
        address.setPhoneNumber(user.getPhoneNumber()); // Hoặc SĐT người nhận riêng
        address.setAddressDetail(addressDetail);
        address.setProvinceCode("20"); // Mã tỉnh Lạng Sơn
        address.setDistrictCode("180"); // Ví dụ: Huyện Cao Lộc
        address.setWardCode("06289");   // Ví dụ: Xã Công Sơn
        address.setDefault(isDefault);
        address.setType(type);
        return address;
    }

    @Test
    void testSaveAddressAndFindById() {
        User user = createAndSaveUser("address.user1@example.com", "0911111111");
        Address address = createAddress(user, "123 Main St, Save Test", false, AddressType.SHIPPING);
        Address savedAddress = addressRepository.save(address);

        assertNotNull(savedAddress.getId());

        Optional<Address> foundAddressOpt = addressRepository.findById(savedAddress.getId());
        assertTrue(foundAddressOpt.isPresent());
        Address foundAddress = foundAddressOpt.get();
        assertEquals("123 Main St, Save Test", foundAddress.getAddressDetail());
        assertEquals(user.getId(), foundAddress.getUser().getId());
    }

    // Các test khác của bạn (findByUserId, findByIdAndUserId, findByUserIdAndIsDefaultTrue, testSoftDeleteAddress)
    // có vẻ đã ổn, chỉ cần đảm bảo User được tạo với Role.
}