package com.yourcompany.agritradels.usermanagement.repository.specification;

import com.yourcompany.agritradels.common.model.RoleType;
import com.yourcompany.agritradels.common.model.VerificationStatus;
import com.yourcompany.agritradels.usermanagement.domain.*; // Import các domain liên quan
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class UserSpecification {

    public static Specification<User> isNotDeleted() {
        // Chỉ cần thiết nếu User entity không có @Where
        // return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
        return (root, query, cb) -> cb.conjunction(); // Trả về true nếu đã có @Where
    }

    public static Specification<User> hasRole(RoleType roleName) {
        return (root, query, cb) -> {
            if (roleName == null) {
                return cb.conjunction();
            }
            Join<User, Role> rolesJoin = root.join("roles", JoinType.INNER);
            return cb.equal(rolesJoin.get("name"), roleName);
        };
    }

    public static Specification<User> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            Predicate nameLike = cb.like(cb.lower(root.get("fullName")), pattern);
            Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
            Predicate phoneLike = cb.like(root.get("phoneNumber"), pattern); // SĐT có thể không cần lower
            return cb.or(nameLike, emailLike, phoneLike);
        };
    }

    public static Specification<User> isActive(Boolean isActive) {
        return (root, query, cb) -> {
            if (isActive == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("isActive"), isActive);
        };
    }

    public static Specification<User> hasFarmerVerificationStatus(VerificationStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            // Cần join với FarmerProfile để lọc theo trạng thái duyệt
            // Đảm bảo User có quan hệ @OneToOne(mappedBy="user") đến FarmerProfile
            // Hoặc join trực tiếp từ FarmerProfile nếu query từ FarmerProfileRepo
            Join<User, FarmerProfile> profileJoin = root.join("farmerProfile", JoinType.LEFT); // Giả sử tên field là farmerProfile
            return cb.equal(profileJoin.get("verificationStatus"), status);
        };
    }
}