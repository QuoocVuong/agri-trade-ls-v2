package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional
public class UserRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb_user_repo")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    private Role roleConsumer, roleFarmer, roleAdmin;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void setUp() {
        // Không cần deleteAll() vì @Transactional sẽ rollback
        // Tạo các role cơ bản nếu chưa có (Flyway nên làm việc này, nhưng để chắc chắn)
        roleConsumer = roleRepository.findByName(RoleType.ROLE_CONSUMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_CONSUMER)));
        roleFarmer = roleRepository.findByName(RoleType.ROLE_FARMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_FARMER)));
        roleAdmin = roleRepository.findByName(RoleType.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleType.ROLE_ADMIN)));
    }

    private User createUser(String email, String phone, String fullName, RoleType... roleTypes) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("testpassword");
        user.setFullName(fullName);
        user.setPhoneNumber(phone);
        user.setActive(true);
        Set<Role> roles = new HashSet<>();
        for (RoleType rt : roleTypes) {
            roleRepository.findByName(rt).ifPresent(roles::add);
        }
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    @Test
    void findByEmail_whenUserExists_returnsUser() {
        createUser("findbyemail@example.com", "001", "Find ByEmail", RoleType.ROLE_CONSUMER);
        Optional<User> found = userRepository.findByEmail("findbyemail@example.com");
        assertTrue(found.isPresent());
        assertEquals("findbyemail@example.com", found.get().getEmail());
    }

    @Test
    void existsByEmailIgnoringSoftDelete_whenUserExists() {
        createUser("existsignore@example.com", "002", "Exists Ignore", RoleType.ROLE_CONSUMER);
        assertTrue(userRepository.existsByEmailIgnoringSoftDelete("existsignore@example.com"));
    }

    @Test
    void findByEmailAndIsActiveTrue_whenActiveUserExists_returnsUser() {
        User activeUser = createUser("active@example.com", "003", "Active User", RoleType.ROLE_CONSUMER);
        activeUser.setActive(true);
        userRepository.save(activeUser);

        Optional<User> found = userRepository.findByEmailAndIsActiveTrue("active@example.com");
        assertTrue(found.isPresent());
        assertTrue(found.get().isActive());
    }

    @Test
    void findByEmailAndIsActiveTrue_whenInactiveUserExists_returnsEmpty() {
        User inactiveUser = createUser("inactive@example.com", "004", "Inactive User", RoleType.ROLE_CONSUMER);
        inactiveUser.setActive(false);
        userRepository.save(inactiveUser);

        Optional<User> found = userRepository.findByEmailAndIsActiveTrue("inactive@example.com");
        assertFalse(found.isPresent());
    }

    @Test
    void findByVerificationToken_success() {
        String token = UUID.randomUUID().toString();
        User userWithToken = createUser("tokenuser@example.com", "005", "Token User", RoleType.ROLE_CONSUMER);
        userWithToken.setVerificationToken(token);
        userRepository.save(userWithToken);

        Optional<User> found = userRepository.findByVerificationToken(token);
        assertTrue(found.isPresent());
        assertEquals(token, found.get().getVerificationToken());
    }

    @Test
    void countByRoleName_returnsCorrectCount() {
        createUser("farmer1@count.com", "006", "Farmer Count 1", RoleType.ROLE_FARMER);
        createUser("farmer2@count.com", "007", "Farmer Count 2", RoleType.ROLE_FARMER);
        createUser("consumer@count.com", "008", "Consumer Count", RoleType.ROLE_CONSUMER);

        long farmerCount = userRepository.countByRoleName(RoleType.ROLE_FARMER);
        assertEquals(2, farmerCount);
        long consumerCount = userRepository.countByRoleName(RoleType.ROLE_CONSUMER);
        assertEquals(1, consumerCount);
    }

    @Test
    void findTopNByOrderByCreatedAtDesc_returnsRecentUsers() {
        User user1 = createUser("recent1@example.com", "009", "Recent User 1", RoleType.ROLE_CONSUMER);
        try { Thread.sleep(10); } catch (InterruptedException e) {} // Đảm bảo thời gian tạo khác nhau
        User user2 = createUser("recent2@example.com", "010", "Recent User 2", RoleType.ROLE_CONSUMER);
        try { Thread.sleep(10); } catch (InterruptedException e) {}
        User user3 = createUser("recent3@example.com", "011", "Recent User 3", RoleType.ROLE_CONSUMER);

        List<User> recentUsers = userRepository.findTopNByOrderByCreatedAtDesc(PageRequest.of(0, 2));
        assertEquals(2, recentUsers.size());
        assertEquals(user3.getId(), recentUsers.get(0).getId()); // User mới nhất
        assertEquals(user2.getId(), recentUsers.get(1).getId());
    }

    @Test
    void findTopByRoles_NameOrderByFollowerCountDesc_returnsCorrectFarmers() {
        User farmerA = createUser("farmerA@top.com", "012", "Farmer A", RoleType.ROLE_FARMER);
        farmerA.setFollowerCount(100);
        userRepository.save(farmerA);

        User farmerB = createUser("farmerB@top.com", "013", "Farmer B", RoleType.ROLE_FARMER);
        farmerB.setFollowerCount(50);
        userRepository.save(farmerB);

        User farmerC = createUser("farmerC@top.com", "014", "Farmer C", RoleType.ROLE_FARMER);
        farmerC.setFollowerCount(150);
        userRepository.save(farmerC);

        User consumerWithFollowers = createUser("consumerTop@top.com", "015", "Top Consumer", RoleType.ROLE_CONSUMER);
        consumerWithFollowers.setFollowerCount(200);
        userRepository.save(consumerWithFollowers);

        Pageable limit = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "followerCount"));
        List<User> topFarmers = userRepository.findTopByRoles_NameOrderByFollowerCountDesc(RoleType.ROLE_FARMER, limit);

        assertNotNull(topFarmers);
        assertEquals(2, topFarmers.size());
        assertEquals(farmerC.getId(), topFarmers.get(0).getId()); // Farmer C - 150 followers
        assertEquals(farmerA.getId(), topFarmers.get(1).getId()); // Farmer A - 100 followers
    }
}