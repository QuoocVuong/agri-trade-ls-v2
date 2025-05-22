package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.AbstractDataJpaTestBase; // Kế thừa từ lớp cha MỚI
import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest // ĐÃ CÓ Ở LỚP CHA AbstractDataJpaTestBase
// @Testcontainers // ĐÃ CÓ Ở LỚP CHA
// @ActiveProfiles("test") // ĐÃ CÓ Ở LỚP CHA
class UserRepositoryIntegrationTest extends AbstractDataJpaTestBase { // Kế thừa từ lớp cha MỚI

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    private User user1;
    private User user2;
    private Role farmerRoleEntity;
    private Role adminRoleEntity;
    private Role buyerRoleEntity;

    private boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM " + tableName + " LIMIT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        if (transactionTemplate == null) {
            transactionTemplate = new TransactionTemplate(transactionManager);
        }

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                clearAndResetTablesWithinTransaction();

                farmerRoleEntity = new Role(RoleType.ROLE_FARMER);
                entityManager.persist(farmerRoleEntity);

                adminRoleEntity = new Role(RoleType.ROLE_ADMIN);
                entityManager.persist(adminRoleEntity);

            
                user1 = new User();
                user1.setEmail("test1@example.com");
                user1.setPasswordHash("passwordhash1");
                user1.setFullName("Test User 1");
                user1.setPhoneNumber("111111111");
                user1.setActive(true);
                user1.setDeleted(false);
                user1.setFollowerCount(10);
                user1.setFollowingCount(5);
                user1.setRoles(new HashSet<>(Set.of(farmerRoleEntity)));
                user1.setProvider("LOCAL");
                user1.setCreatedAt(LocalDateTime.now().minusDays(5));
                entityManager.persist(user1);

                user2 = new User();
                user2.setEmail("test2@example.com");
                user2.setPasswordHash("passwordhash2");
                user2.setFullName("Test User 2");
                user2.setPhoneNumber("222222222");
                user2.setActive(false);
                user2.setDeleted(false);
                user2.setFollowerCount(20);
                user2.setFollowingCount(15);
                user2.setRoles(new HashSet<>(Set.of(buyerRoleEntity))); // Gán buyer role
                user2.setProvider("LOCAL");
                user2.setCreatedAt(LocalDateTime.now().minusDays(10));
                entityManager.persist(user2);

                entityManager.flush();
            }
        });
    }

    private void clearAndResetTablesWithinTransaction() {
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0;").executeUpdate();
        if (tableExists("user_roles")) {
            entityManager.createNativeQuery("TRUNCATE TABLE user_roles;").executeUpdate();
        }
        if (tableExists("role_permissions")) {
            entityManager.createNativeQuery("TRUNCATE TABLE role_permissions;").executeUpdate();
        }
        if (tableExists("permissions")) {
            entityManager.createNativeQuery("TRUNCATE TABLE permissions;").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE permissions AUTO_INCREMENT = 1;").executeUpdate();
        }
        if (tableExists("users")) {
            entityManager.createNativeQuery("TRUNCATE TABLE users;").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE users AUTO_INCREMENT = 1;").executeUpdate();
        }
        if (tableExists("roles")) {
            entityManager.createNativeQuery("TRUNCATE TABLE roles;").executeUpdate();
            entityManager.createNativeQuery("ALTER TABLE roles AUTO_INCREMENT = 1;").executeUpdate();
        }
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1;").executeUpdate();
        entityManager.flush();
    }

    // --- CÁC @Test METHOD CỦA BẠN GIỮ NGUYÊN ---
    // (Giữ nguyên các test method như bạn đã cung cấp)
    @Test
    @DisplayName("Khi tìm bằng email hợp lệ và chưa bị xóa, trả về User")
    void whenFindByEmail_thenReturnUser() {
        Optional<User> foundUser = userRepository.findByEmail("test1@example.com");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test1@example.com");
        assertThat(foundUser.get().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Khi tìm bằng email của User đã bị xóa (soft delete), trả về Optional rỗng")
    void whenFindByEmail_withDeletedUser_thenReturnEmptyOptional() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User deletedUser = new User();
                deletedUser.setEmail("deleted@example.com");
                deletedUser.setPasswordHash("passwordhash_deleted");
                deletedUser.setFullName("Deleted User");
                deletedUser.setPhoneNumber("333333333");
                deletedUser.setActive(true);
                deletedUser.setDeleted(true);
                deletedUser.setRoles(new HashSet<>(Set.of(farmerRoleEntity)));
                deletedUser.setProvider("LOCAL");
                entityManager.persist(deletedUser);
                entityManager.flush();
            }
        });
        Optional<User> foundUser = userRepository.findByEmail("deleted@example.com");
        assertThat(foundUser).isNotPresent();
    }


    @Test
    @DisplayName("Kiểm tra tồn tại bằng email, trả về true nếu email tồn tại và chưa bị xóa")
    void whenExistsByEmail_thenReturnTrueForExistingEmail() {
        Boolean exists = userRepository.existsByEmail("test1@example.com");
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Kiểm tra tồn tại bằng email, trả về false nếu email không tồn tại")
    void whenExistsByEmail_thenReturnFalseForNonExistingEmail() {
        Boolean exists = userRepository.existsByEmail("nonexistent@example.com");
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Kiểm tra tồn tại bằng email, trả về false nếu email tồn tại nhưng đã bị xóa")
    void whenExistsByEmail_thenReturnFalseForDeletedEmail() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User deletedUser = new User();
                deletedUser.setEmail("deleted.exists@example.com");
                deletedUser.setPasswordHash("password");
                deletedUser.setFullName("Deleted User Exists");
                deletedUser.setDeleted(true);
                entityManager.persist(deletedUser);
                entityManager.flush();
            }
        });
        Boolean exists = userRepository.existsByEmail("deleted.exists@example.com");
        assertThat(exists).isFalse();
    }


    @Test
    @DisplayName("Kiểm tra tồn tại bằng số điện thoại, trả về true nếu SĐT tồn tại và chưa bị xóa")
    void whenExistsByPhoneNumber_thenReturnTrueForExistingPhoneNumber() {
        Boolean exists = userRepository.existsByPhoneNumber("111111111");
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Kiểm tra tồn tại bằng số điện thoại, trả về false nếu SĐT không tồn tại")
    void whenExistsByPhoneNumber_thenReturnFalseForNonExistingPhoneNumber() {
        Boolean exists = userRepository.existsByPhoneNumber("999999999");
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Kiểm tra tồn tại bằng email (bỏ qua soft delete), trả về true cho email đã bị xóa")
    void whenExistsByEmailIgnoringSoftDelete_thenReturnTrueForExistingEmailIncludingDeleted() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User deletedUser = new User();
                deletedUser.setEmail("deleted_ignore@example.com");
                deletedUser.setPasswordHash("password");
                deletedUser.setFullName("Deleted User Ignore");
                deletedUser.setDeleted(true);
                entityManager.persist(deletedUser);
                entityManager.flush();
            }
        });
        boolean exists = userRepository.existsByEmailIgnoringSoftDelete("deleted_ignore@example.com");
        assertThat(exists).isTrue();
    }


    @Test
    @DisplayName("Kiểm tra tồn tại bằng SĐT (bỏ qua soft delete), trả về true cho SĐT đã bị xóa")
    void whenExistsByPhoneNumberIgnoringSoftDelete_thenReturnTrueForExistingPhoneNumberIncludingDeleted() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User deletedUser = new User();
                deletedUser.setEmail("another_deleted_ignore@example.com");
                deletedUser.setPhoneNumber("555555555");
                deletedUser.setPasswordHash("password");
                deletedUser.setFullName("Another Deleted User Ignore");
                deletedUser.setDeleted(true);
                entityManager.persist(deletedUser);
                entityManager.flush();
            }
        });
        boolean exists = userRepository.existsByPhoneNumberIgnoringSoftDelete("555555555");
        assertThat(exists).isTrue();
    }


    @Test
    @DisplayName("Tìm bằng email và isActive=true, trả về User nếu active và chưa bị xóa")
    void whenFindByEmailAndIsActiveTrue_thenReturnActiveUser() {
        Optional<User> foundUser = userRepository.findByEmailAndIsActiveTrue("test1@example.com");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test1@example.com");
        assertThat(foundUser.get().isActive()).isTrue();
        assertThat(foundUser.get().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Tìm bằng email và isActive=true, trả về Optional rỗng nếu user không active")
    void whenFindByEmailAndIsActiveTrue_thenReturnEmptyOptionalForInactiveUser() {
        Optional<User> foundUser = userRepository.findByEmailAndIsActiveTrue("test2@example.com");
        assertThat(foundUser).isNotPresent();
    }


    @Test
    @DisplayName("Tìm bằng ID (bao gồm cả đã xóa), trả về User đã bị xóa")
    void whenFindByIdIncludingDeleted_thenReturnUserIncludingDeleted() {
        final User[] persistedDeletedUser = new User[1];
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User deletedUser = new User();
                deletedUser.setEmail("findbyid_deleted@example.com");
                deletedUser.setPasswordHash("password");
                deletedUser.setFullName("Find By ID Deleted User");
                deletedUser.setDeleted(true);
                entityManager.persist(deletedUser);
                entityManager.flush();
                persistedDeletedUser[0] = deletedUser;
            }
        });
        Optional<User> foundUser = userRepository.findByIdIncludingDeleted(persistedDeletedUser[0].getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getId()).isEqualTo(persistedDeletedUser[0].getId());
        assertThat(foundUser.get().isDeleted()).isTrue();
    }


    @Test
    @DisplayName("Tìm bằng verification token, trả về User nếu token khớp")
    void whenFindByVerificationToken_thenReturnUser() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User userWithToken = new User();
                userWithToken.setEmail("tokenuser@example.com");
                userWithToken.setPasswordHash("password");
                userWithToken.setFullName("Token User");
                userWithToken.setVerificationToken("valid-token");
                entityManager.persist(userWithToken);
                entityManager.flush();
            }
        });
        Optional<User> foundUser = userRepository.findByVerificationToken("valid-token");
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getVerificationToken()).isEqualTo("valid-token");
    }


    @Test
    @DisplayName("Đếm số lượng User theo tên Role, trả về số lượng chính xác")
    void whenCountByRoleName_thenReturnCountOfUsersWithRole() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User adminUser = new User();
                adminUser.setEmail("admin@example.com");
                adminUser.setPasswordHash("password");
                adminUser.setFullName("Admin User");
                adminUser.setRoles(new HashSet<>(Set.of(adminRoleEntity)));
                entityManager.persist(adminUser);
                entityManager.flush();
            }
        });
        long farmerCount = userRepository.countByRoleName(RoleType.ROLE_FARMER);
        long adminCount = userRepository.countByRoleName(RoleType.ROLE_ADMIN);


        assertThat(farmerCount).isEqualTo(1);
        assertThat(adminCount).isEqualTo(1);

    }


    @Test
    @DisplayName("Lấy top N Users sắp xếp theo ngày tạo giảm dần")
    void whenFindTopNByOrderByCreatedAtDesc_thenReturnTopNUsers() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User user3 = new User();
                user3.setEmail("test3@example.com");
                user3.setPasswordHash("password");
                user3.setFullName("Test User 3");
                user3.setCreatedAt(LocalDateTime.now());
                entityManager.persist(user3);
                entityManager.flush();
            }
        });
        Pageable pageable = PageRequest.of(0, 2);
        List<User> topUsers = userRepository.findTopNByOrderByCreatedAtDesc(pageable);

        assertThat(topUsers).hasSize(2);
        assertThat(topUsers.get(0).getFullName()).isEqualTo("Test User 3");
        assertThat(topUsers.get(1).getFullName()).isEqualTo("Test User 1");
    }

    @Test
    @DisplayName("Kiểm tra tồn tại bằng ID và tên Role, trả về true nếu User có Role đó")
    void whenExistsByIdAndRoles_Name_thenReturnTrueForUserWithRole() {
        boolean exists = userRepository.existsByIdAndRoles_Name(user1.getId(), RoleType.ROLE_FARMER);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Kiểm tra tồn tại bằng ID và tên Role, trả về false nếu User không có Role đó")
    void whenExistsByIdAndRoles_Name_thenReturnFalseForUserWithoutRole() {
        boolean exists = userRepository.existsByIdAndRoles_Name(user1.getId(), RoleType.ROLE_ADMIN);
        assertThat(exists).isFalse();
    }


    @Test
    @DisplayName("Lấy top User theo tên Role sắp xếp theo số lượng follower giảm dần")
    void whenFindTopByRoles_NameOrderByFollowerCountDesc_thenReturnTopFarmers() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                User farmer2 = new User();
                farmer2.setEmail("farmer2@example.com");
                farmer2.setPasswordHash("password");
                farmer2.setFullName("Farmer Two");
                farmer2.setFollowerCount(50);
                farmer2.setRoles(new HashSet<>(Set.of(farmerRoleEntity)));
                entityManager.persist(farmer2);

                User farmer3 = new User();
                farmer3.setEmail("farmer3@example.com");
                farmer3.setPasswordHash("password");
                farmer3.setFullName("Farmer Three");
                farmer3.setFollowerCount(100);
                farmer3.setRoles(new HashSet<>(Set.of(farmerRoleEntity)));
                entityManager.persist(farmer3);
                entityManager.flush();
            }
        });
        Pageable pageable = PageRequest.of(0, 2);
        List<User> topFarmers = userRepository.findTopByRoles_NameOrderByFollowerCountDesc(RoleType.ROLE_FARMER, pageable);

        assertThat(topFarmers).hasSize(2);
        assertThat(topFarmers.get(0).getFullName()).isEqualTo("Farmer Three");
        assertThat(topFarmers.get(1).getFullName()).isEqualTo("Farmer Two");
    }
}