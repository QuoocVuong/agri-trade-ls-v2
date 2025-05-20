package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.usermanagement.domain.Permission;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
  Optional<Permission> findByName(String name);

  boolean existsByName(String name);

  // Tìm các permission theo danh sách tên (hữu ích khi gán cho role)
  Set<Permission> findByNameIn(Set<String> names);
}
