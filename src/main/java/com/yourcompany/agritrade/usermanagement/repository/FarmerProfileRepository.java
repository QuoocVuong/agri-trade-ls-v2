package com.yourcompany.agritrade.usermanagement.repository;

import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, Long>, JpaSpecificationExecutor<FarmerProfile> {
    // Optional<FarmerProfile> findByUserId(Long userId); // Không cần vì Id là userId


    // Đếm farmer theo trạng thái duyệt (cho Admin)
    long countByVerificationStatus(VerificationStatus status);
    // *** Thêm phương thức mới ***
    // Sử dụng @EntityGraph để fetch User cùng lúc, tránh N+1 query
    @EntityGraph(attributePaths = {"user", "user.roles"}) // Fetch user và roles của user đó
    Page<FarmerProfile> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}