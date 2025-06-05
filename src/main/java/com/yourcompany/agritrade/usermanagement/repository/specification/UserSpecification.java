package com.yourcompany.agritrade.usermanagement.repository.specification;

import com.yourcompany.agritrade.common.model.RoleType;
import com.yourcompany.agritrade.common.model.VerificationStatus;
import com.yourcompany.agritrade.usermanagement.domain.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class UserSpecification {

  public static Specification<User> isNotDeleted() {

    return (root, query, cb) -> cb.conjunction();
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

      Join<User, FarmerProfile> profileJoin =
          root.join("farmerProfile", JoinType.LEFT);
      return cb.equal(profileJoin.get("verificationStatus"), status);
    };
  }
}
