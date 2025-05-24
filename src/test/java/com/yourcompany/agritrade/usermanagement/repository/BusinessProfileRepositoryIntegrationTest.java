package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.AgriTradeApplication;
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.BusinessProfile;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AgriTradeApplication.class)
@Testcontainers
@Transactional
public class BusinessProfileRepositoryIntegrationTest {

    @Container
    public static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb_bizprofile_repo")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testBusinessUser;
    private Role businessBuyerRole;

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
        businessProfileRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();

        businessBuyerRole = roleRepository.findByName(RoleType.ROLE_BUSINESS_BUYER)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(RoleType.ROLE_BUSINESS_BUYER);
                    return roleRepository.save(newRole);
                });

        testBusinessUser = new User();
        testBusinessUser.setEmail("business.profile.user@example.com");
        testBusinessUser.setPasswordHash("password123");
        testBusinessUser.setFullName("Business Profile Test User");
        testBusinessUser.setPhoneNumber("0987650001");
        testBusinessUser.setActive(true);
        Set<Role> roles = new HashSet<>();
        roles.add(businessBuyerRole);
        testBusinessUser.setRoles(roles);
        testBusinessUser = userRepository.saveAndFlush(testBusinessUser);
    }

    @Test
    void testSaveAndFindById_success() {
        // Arrange
        BusinessProfile profile = new BusinessProfile();
        profile.setUser(testBusinessUser); // Liên kết với User
        profile.setUserId(testBusinessUser.getId()); // Đặt userId (khóa chính)
        profile.setBusinessName("My Awesome Business");
        profile.setTaxCode("TAXCODE123");
        profile.setIndustry("E-commerce");
        profile.setBusinessPhone("02838123456");
        profile.setBusinessAddressDetail("123 Business Rd, Ward 4, District 1");
        profile.setBusinessProvinceCode("79"); // Ví dụ: TP HCM
        profile.setBusinessDistrictCode("760");
        profile.setBusinessWardCode("26708");
        profile.setContactPerson("Mr. Business");

        // Act
        BusinessProfile savedProfile = businessProfileRepository.saveAndFlush(profile);

        // Assert
        assertNotNull(savedProfile);
        assertEquals(testBusinessUser.getId(), savedProfile.getUserId()); // ID phải khớp với User ID

        Optional<BusinessProfile> foundProfileOpt = businessProfileRepository.findById(savedProfile.getUserId());
        assertTrue(foundProfileOpt.isPresent());
        BusinessProfile foundProfile = foundProfileOpt.get();

        assertEquals("My Awesome Business", foundProfile.getBusinessName());
        assertEquals("TAXCODE123", foundProfile.getTaxCode());
        assertEquals(testBusinessUser.getId(), foundProfile.getUser().getId());
    }

    @Test
    void testSave_whenUserNotExists_shouldFailOrHandleGracefully() {
        // Arrange
        User nonExistentUser = new User();
        // KHÔNG save nonExistentUser vào DB
        // nonExistentUser.setId(999L); // Giả sử một ID không tồn tại

        BusinessProfile profile = new BusinessProfile();
        // profile.setUser(nonExistentUser); // Gán user không được quản lý (transient)
        // profile.setUserId(999L); // Hoặc gán một userId không tồn tại

        // Nếu bạn cố gắng save một BusinessProfile với một User chưa được persist,
        // và không có CascadeType.PERSIST từ BusinessProfile đến User (điều này không nên có),
        // bạn sẽ gặp lỗi.
        // Hoặc nếu bạn set một userId không tồn tại trong bảng users, khóa ngoại sẽ báo lỗi.

        // Kịch bản 1: User là transient
        profile.setUser(nonExistentUser); // User này chưa được save
        profile.setBusinessName("Business With Transient User");
        profile.setBusinessProvinceCode("01");

        // Hibernate sẽ cố gắng persist User nếu có CascadeType.PERSIST hoặc MERGE từ BusinessProfile.
        // Nếu không, và user_id là NOT NULL, sẽ có lỗi.
        // Với @MapsId, user_id là khóa chính và cũng là khóa ngoại, nên User phải tồn tại.
        assertThrows(Exception.class, () -> { // Có thể là DataIntegrityViolationException hoặc InvalidDataAccessApiUsageException
            businessProfileRepository.saveAndFlush(profile);
        }, "Saving BusinessProfile with a transient User or non-existent userId should fail");
    }

    @Test
    void testUniqueConstraint_taxCode() {
        // Arrange
        BusinessProfile profile1 = new BusinessProfile();
        profile1.setUser(testBusinessUser);
        profile1.setUserId(testBusinessUser.getId());
        profile1.setBusinessName("Business One");
        profile1.setTaxCode("UNIQUE_TAX_001");
        profile1.setBusinessProvinceCode("20");
        businessProfileRepository.saveAndFlush(profile1);

        // Tạo user thứ hai để tránh lỗi unique user_id trên BusinessProfile
        User anotherUser = new User();
        anotherUser.setEmail("another.biz@example.com");
        anotherUser.setPasswordHash("pw");
        anotherUser.setFullName("Another Biz User");
        anotherUser.setPhoneNumber("0900000011");
        anotherUser.setActive(true);
        anotherUser.setRoles(Collections.singleton(businessBuyerRole));
        userRepository.saveAndFlush(anotherUser);

        BusinessProfile profile2 = new BusinessProfile();
        profile2.setUser(anotherUser);
        profile2.setUserId(anotherUser.getId());
        profile2.setBusinessName("Business Two");
        profile2.setTaxCode("UNIQUE_TAX_001"); // Trùng taxCode
        profile2.setBusinessProvinceCode("20");

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            businessProfileRepository.saveAndFlush(profile2);
        }, "Should throw DataIntegrityViolationException for duplicate taxCode");
    }

    @Test
    void testDeleteBusinessProfile_shouldAlsoDeleteUserIfCascadeIsSetOnUserSide() {
        // Arrange
        BusinessProfile profile = new BusinessProfile();
        profile.setUser(testBusinessUser);
        profile.setUserId(testBusinessUser.getId());
        profile.setBusinessName("To Be Deleted Business");
        profile.setBusinessProvinceCode("20");
        BusinessProfile savedProfile = businessProfileRepository.saveAndFlush(profile);
        Long userId = testBusinessUser.getId();

        assertTrue(userRepository.existsById(userId));
        assertTrue(businessProfileRepository.existsById(userId));

        // Act
        // Khi xóa User, BusinessProfile cũng nên bị xóa do CascadeType.ALL và orphanRemoval=true
        // trên User.businessProfile (nếu bạn có mối quan hệ hai chiều và User là chủ sở hữu)
        // Hoặc nếu bạn xóa BusinessProfile, User có bị ảnh hưởng không?
        // Với @MapsId, BusinessProfile không thể tồn tại mà không có User.
        // Nếu bạn xóa User, BusinessProfile sẽ bị xóa theo (do khóa ngoại và có thể cascade).
        // Nếu bạn xóa BusinessProfile, User vẫn còn.

        businessProfileRepository.deleteById(savedProfile.getUserId());
        businessProfileRepository.flush(); // Đảm bảo thay đổi được ghi xuống DB

        // Assert
        assertFalse(businessProfileRepository.existsById(savedProfile.getUserId()));
        assertTrue(userRepository.existsById(userId)); // User không nên bị xóa khi chỉ xóa BusinessProfile
    }
}