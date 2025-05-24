package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional
public class FarmerProfileRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb_farmerprofile_repo")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired private FarmerProfileRepository farmerProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private User farmerUser1, farmerUser2, adminUser;
    private Role farmerRole;

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
        farmerProfileRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        farmerRole = roleRepository.findByName(RoleType.ROLE_FARMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_FARMER)));

        adminUser = createUser("admin.fp@example.com", "000", "Admin FP", RoleType.ROLE_ADMIN); // Admin để duyệt

        farmerUser1 = createUser("farmer1.fp@example.com", "111", "Farmer One FP", RoleType.ROLE_FARMER);
        farmerUser2 = createUser("farmer2.fp@example.com", "222", "Farmer Two FP", RoleType.ROLE_FARMER);
    }

    private User createUser(String email, String phone, String fullName, RoleType roleType) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("testpassword");
        user.setFullName(fullName);
        user.setPhoneNumber(phone);
        user.setActive(true);
        Set<Role> roles = new HashSet<>();
        roleRepository.findByName(roleType).ifPresent(roles::add);
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    private FarmerProfile createFarmerProfile(User user, String farmName, VerificationStatus status) {
        FarmerProfile profile = new FarmerProfile();
        profile.setUser(user); // Quan trọng: Liên kết User
        profile.setUserId(user.getId()); // Quan trọng: Đặt userId
        profile.setFarmName(farmName);
        profile.setProvinceCode("20"); // Lạng Sơn
        profile.setVerificationStatus(status);
        if (status == VerificationStatus.VERIFIED || status == VerificationStatus.REJECTED) {
            profile.setVerifiedBy(adminUser);
        }
        return farmerProfileRepository.saveAndFlush(profile);
    }

    @Test
    void countByVerificationStatus_returnsCorrectCounts() {
        createFarmerProfile(farmerUser1, "Farm A", VerificationStatus.PENDING);
        createFarmerProfile(farmerUser2, "Farm B", VerificationStatus.VERIFIED);
        User farmerUser3 = createUser("farmer3.fp@example.com", "333", "Farmer Three FP", RoleType.ROLE_FARMER);
        createFarmerProfile(farmerUser3, "Farm C", VerificationStatus.PENDING);

        assertEquals(2, farmerProfileRepository.countByVerificationStatus(VerificationStatus.PENDING));
        assertEquals(1, farmerProfileRepository.countByVerificationStatus(VerificationStatus.VERIFIED));
        assertEquals(0, farmerProfileRepository.countByVerificationStatus(VerificationStatus.REJECTED));
    }

    @Test
    void findByVerificationStatus_withPageable_returnsPagedResults() {
        createFarmerProfile(farmerUser1, "Farm Pending 1", VerificationStatus.PENDING);
        User farmerUser3 = createUser("farmer3.fp@example.com", "333", "Farmer Three FP", RoleType.ROLE_FARMER);
        createFarmerProfile(farmerUser3, "Farm Pending 2", VerificationStatus.PENDING);
        createFarmerProfile(farmerUser2, "Farm Verified 1", VerificationStatus.VERIFIED);

        Pageable pageable = PageRequest.of(0, 1);
        Page<FarmerProfile> pendingPage = farmerProfileRepository.findByVerificationStatus(VerificationStatus.PENDING, pageable);

        assertNotNull(pendingPage);
        assertEquals(2, pendingPage.getTotalElements()); // Tổng số PENDING
        assertEquals(1, pendingPage.getContent().size()); // Số lượng trên trang hiện tại
        assertEquals(VerificationStatus.PENDING, pendingPage.getContent().get(0).getVerificationStatus());

        Pageable pageableAll = PageRequest.of(0, 5);
        Page<FarmerProfile> verifiedPage = farmerProfileRepository.findByVerificationStatus(VerificationStatus.VERIFIED, pageableAll);
        assertEquals(1, verifiedPage.getTotalElements());
    }
}