package com.yourcompany.agritradels.config;

import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.usermanagement.domain.Role;
import com.yourcompany.agritradels.usermanagement.domain.User;
import com.yourcompany.agritradels.usermanagement.repository.RoleRepository;
import com.yourcompany.agritradels.usermanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // Import

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // Lấy thông tin admin từ application.yaml hoặc biến môi trường
     @Value("${app.admin.email}") // Ví dụ lấy từ properties
     private String adminEmail;
     @Value("${app.admin.password}")
     private String adminPassword;
     @Value("${app.admin.fullname}")
     private String adminFullName;

    // Hoặc hardcode (không khuyến khích cho production)
//    private final String adminEmail = "admin@agritradels.com";
//    private final String adminPassword = "SecureAdminPassword123!"; // Đặt mật khẩu mạnh
//    private final String adminFullName = "Quản Trị Viên";


    @Override
    @Transactional // Cần Transactional để thao tác với DB
    public void run(String... args) throws Exception {
        log.info("Running Data Initializer...");

        // 1. Đảm bảo các Roles cơ bản tồn tại
        initializeRoleIfNotExists(RoleType.ROLE_ADMIN);
        initializeRoleIfNotExists(RoleType.ROLE_FARMER);
        initializeRoleIfNotExists(RoleType.ROLE_CONSUMER);
        initializeRoleIfNotExists(RoleType.ROLE_BUSINESS_BUYER);

        // 2. Kiểm tra và tạo Admin User nếu chưa có
        if (!userRepository.existsByEmail(adminEmail)) {
            log.info("Creating initial Admin user...");
            User adminUser = new User();
            adminUser.setEmail(adminEmail);
            adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
            adminUser.setFullName(adminFullName);
            adminUser.setActive(true); // Admin phải active

            Role adminRole = roleRepository.findByName(RoleType.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Error: ADMIN Role not found."));
            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            adminUser.setRoles(roles);

            userRepository.save(adminUser);
            log.info("Initial Admin user created successfully with email: {}", adminEmail);
        } else {
            log.info("Admin user already exists.");
        }
        log.info("Data Initializer finished.");
    }

    private void initializeRoleIfNotExists(RoleType roleType) {
        if (!roleRepository.findByName(roleType).isPresent()) {
            log.info("Creating role: {}", roleType);
            roleRepository.save(new Role(roleType));
        }
    }
}