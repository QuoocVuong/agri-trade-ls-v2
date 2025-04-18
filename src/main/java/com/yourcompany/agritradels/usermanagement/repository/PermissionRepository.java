package com.yourcompany.agritradels.usermanagement.repository;

import com.yourcompany.agritradels.usermanagement.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.Set; // Import Set

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    Optional<Permission> findByName(String name);
    boolean existsByName(String name);
    // Tìm các permission theo danh sách tên (hữu ích khi gán cho role)
    Set<Permission> findByNameIn(Set<String> names);
}