package com.yourcompany.agritrade.config;

import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.Permission;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.repository.PermissionRepository;
import com.yourcompany.agritrade.usermanagement.repository.RoleRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final PasswordEncoder passwordEncoder;
  private final PermissionRepository permissionRepository;

  @Value("${app.admin.email}")
  private String adminEmail;

  @Value("${app.admin.password}")
  private String adminPassword;

  @Value("${app.admin.fullname}")
  private String adminFullName;

  // Định nghĩa các quyền
  private static final List<PermissionData> ALL_PERMISSIONS =
      Arrays.asList(
          new PermissionData("USER_READ_ALL", "Quyền xem danh sách tất cả người dùng"),
          new PermissionData("USER_READ_DETAIL", "Quyền xem chi tiết người dùng"),
          new PermissionData("USER_UPDATE_STATUS", "Quyền cập nhật trạng thái active/inactive"),
          new PermissionData("USER_UPDATE_ROLES", "Quyền cập nhật vai trò người dùng"),
          new PermissionData("USER_MANAGE_PROFILES", "Quyền quản lý (duyệt/từ chối) profiles"),
          new PermissionData(
              "PRODUCT_MANAGE_OWN", "Quyền quản lý sản phẩm của chính mình (Farmer)"),
          new PermissionData("PRODUCT_MANAGE_ALL", "Quyền quản lý tất cả sản phẩm (Admin)"),
          new PermissionData("PRODUCT_READ", "Quyền xem sản phẩm"),
          new PermissionData("ORDER_CREATE", "Quyền tạo đơn hàng (Consumer, Business Buyer)"),
          new PermissionData("ORDER_READ_OWN", "Quyền xem đơn hàng của mình (Buyer, Farmer)"),
          new PermissionData("ORDER_READ_ALL", "Quyền xem tất cả đơn hàng (Admin)"),
          new PermissionData(
              "ORDER_UPDATE_STATUS_OWN", "Quyền cập nhật trạng thái đơn hàng của mình (Farmer)"),
          new PermissionData(
              "ORDER_UPDATE_STATUS_ALL", "Quyền cập nhật trạng thái mọi đơn hàng (Admin)"),
          new PermissionData("PERMISSION_MANAGE", "Quyền quản lý các quyền hạn khác"));

  // Định nghĩa việc gán quyền cho vai trò
  private static final Map<RoleType, List<String>> ROLE_PERMISSION_MAPPING =
      Map.of(
          RoleType.ROLE_ADMIN,
              List.of(
                  "USER_READ_ALL",
                  "USER_READ_DETAIL",
                  "USER_UPDATE_STATUS",
                  "USER_UPDATE_ROLES",
                  "USER_MANAGE_PROFILES",
                  "PRODUCT_MANAGE_ALL",
                  "PRODUCT_READ",
                  "ORDER_READ_ALL",
                  "ORDER_UPDATE_STATUS_ALL",
                  "PERMISSION_MANAGE" // Admin có quyền quản lý permission
                  ),
          RoleType.ROLE_FARMER,
              List.of(
                  "PRODUCT_MANAGE_OWN",
                  "ORDER_READ_OWN",
                  "ORDER_UPDATE_STATUS_OWN",
                  "PRODUCT_READ"),
          RoleType.ROLE_CONSUMER, List.of("ORDER_CREATE", "ORDER_READ_OWN", "PRODUCT_READ"),
          RoleType.ROLE_BUSINESS_BUYER, List.of("ORDER_CREATE", "ORDER_READ_OWN", "PRODUCT_READ"));

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    log.info("Running Data Initializer...");

    // 1. Đảm bảo các Permissions cơ bản tồn tại
    initializePermissions();

    // 2. Đảm bảo các Roles cơ bản tồn tại và được gán Permissions
    initializeRoleAndPermissions(RoleType.ROLE_ADMIN);
    initializeRoleAndPermissions(RoleType.ROLE_FARMER);
    initializeRoleAndPermissions(RoleType.ROLE_CONSUMER);
    initializeRoleAndPermissions(RoleType.ROLE_BUSINESS_BUYER);

    // 3. Kiểm tra và tạo Admin User nếu chưa có
    if (!userRepository.existsByEmail(adminEmail)) {
      log.info("Creating initial Admin user...");
      User adminUser = new User();
      adminUser.setEmail(adminEmail);
      adminUser.setPasswordHash(passwordEncoder.encode(adminPassword));
      adminUser.setFullName(adminFullName);
      adminUser.setActive(true);

      Role adminRole =
          roleRepository
              .findByName(RoleType.ROLE_ADMIN)
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

  private void initializePermissions() {
    for (PermissionData pData : ALL_PERMISSIONS) {
      if (!permissionRepository.existsByName(pData.name())) {
        permissionRepository.save(new Permission(pData.name(), pData.description()));
        log.info("Created permission: {}", pData.name());
      }
    }
  }

  private void initializeRoleAndPermissions(RoleType roleType) {
    Role role =
        roleRepository
            .findByName(roleType)
            .orElseGet(
                () -> {
                  log.info("Creating role: {}", roleType);
                  return roleRepository.save(new Role(roleType));
                });

    // Gán permissions cho role nếu role mới được tạo hoặc chưa có permission nào

    List<String> expectedPermissionNames = ROLE_PERMISSION_MAPPING.get(roleType);
    if (expectedPermissionNames != null && !expectedPermissionNames.isEmpty()) {
      Set<Permission> currentPermissions = role.getPermissions();
      Set<String> currentPermissionNames =
          currentPermissions.stream().map(Permission::getName).collect(Collectors.toSet());

      List<String> permissionsToAssignNames =
          expectedPermissionNames.stream()
              .filter(name -> !currentPermissionNames.contains(name))
              .collect(Collectors.toList());

      if (!permissionsToAssignNames.isEmpty()) {
        Set<Permission> permissionsToAssign =
            permissionRepository.findByNameIn(new HashSet<>(permissionsToAssignNames));
        if (permissionsToAssign.size() != permissionsToAssignNames.size()) {
          log.warn(
              "Could not find all expected permissions for role {}: Missing {}",
              roleType,
              permissionsToAssignNames.stream()
                  .filter(
                      name -> permissionsToAssign.stream().noneMatch(p -> p.getName().equals(name)))
                  .collect(Collectors.toList()));
        }
        currentPermissions.addAll(permissionsToAssign);
        role.setPermissions(currentPermissions);
        roleRepository.save(role);
        log.info(
            "Assigned/Updated permissions for role {}: {}",
            roleType,
            permissionsToAssign.stream().map(Permission::getName).collect(Collectors.toList()));
      }
    }
  }

  // Lớp nội bộ để lưu trữ dữ liệu permission
  private record PermissionData(String name, String description) {}
}
