package com.yourcompany.agritradels.usermanagement.repository;

import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.usermanagement.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(RoleType name);
}