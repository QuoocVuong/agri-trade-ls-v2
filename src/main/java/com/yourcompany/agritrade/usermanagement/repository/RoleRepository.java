package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.usermanagement.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Integer> {
  Optional<Role> findByName(RoleType name);
}
