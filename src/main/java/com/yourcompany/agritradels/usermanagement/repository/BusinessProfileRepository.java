package com.yourcompany.agritradels.usermanagement.repository;

import com.yourcompany.agritradels.usermanagement.domain.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {
    // Optional<BusinessProfile> findByUserId(Long userId); // Không cần
}