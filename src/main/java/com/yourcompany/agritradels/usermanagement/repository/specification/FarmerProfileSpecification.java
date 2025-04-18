// File: usermanagement/repository/specification/FarmerProfileSpecification.java (Mới)
package com.yourcompany.agritradels.usermanagement.repository.specification;

import com.yourcompany.agritradels.common.model.VerificationStatus;
import com.yourcompany.agritradels.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritradels.usermanagement.domain.User;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class FarmerProfileSpecification {

    public static Specification<FarmerProfile> hasVerificationStatus(VerificationStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("verificationStatus"), status);
        };
    }

    public static Specification<FarmerProfile> userHasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            // Join với User để tìm kiếm theo thông tin User
            Join<FarmerProfile, User> userJoin = root.join("user", JoinType.INNER);
            String pattern = "%" + keyword.toLowerCase() + "%";

            Predicate farmNameLike = cb.like(cb.lower(root.get("farmName")), pattern); // Tìm theo tên trang trại
            Predicate userNameLike = cb.like(cb.lower(userJoin.get("fullName")), pattern);
            Predicate emailLike = cb.like(cb.lower(userJoin.get("email")), pattern);
            Predicate phoneLike = cb.like(userJoin.get("phoneNumber"), pattern);

            return cb.or(farmNameLike, userNameLike, emailLike, phoneLike);
        };
    }
}