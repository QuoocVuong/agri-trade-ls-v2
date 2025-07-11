package com.yourcompany.agritrade.catalog.repository.specification;

import com.yourcompany.agritrade.catalog.domain.Category;
import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.catalog.domain.ProductStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class ProductSpecifications {

  public static Specification<Product> fetchFarmerAndProfile() {
    return (root, query, cb) -> {
      // Chỉ thực hiện fetch khi query không phải là count query
      if (Long.class != query.getResultType() && long.class != query.getResultType()) {
        // Fetch farmer (User)
        Fetch<Product, User> farmerFetch =
            root.fetch("farmer", JoinType.INNER); // INNER vì product phải có farmer
        // Fetch farmerProfile từ farmer đã fetch
        farmerFetch.fetch("farmerProfile", JoinType.LEFT); // LEFT vì farmer có thể chưa có profile
      }
      // Trả về một Predicate luôn đúng
      return cb.conjunction();
    };
  }

  // Specification: Lấy sản phẩm có trạng thái PUBLISHED
  public static Specification<Product> isPublished() {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.equal(root.get("status"), ProductStatus.PUBLISHED);
  }

  // Specification: Tìm kiếm theo keyword trong tên hoặc mô tả
  public static Specification<Product> hasKeyword(String keyword) {
    return (root, query, criteriaBuilder) -> {
      if (!StringUtils.hasText(keyword)) {
        return criteriaBuilder.conjunction(); // Không có điều kiện nếu keyword rỗng
      }
      String pattern = "%" + keyword.toLowerCase() + "%";
      // Tạo biểu thức OR cho tìm kiếm trong name hoặc description
      Predicate nameLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern);
      Predicate descriptionLike =
          criteriaBuilder.like(root.get("description"), "%" + keyword + "%");
      return criteriaBuilder.or(nameLike, descriptionLike);
    };
  }

  // Specification: Lọc theo categoryId
  public static Specification<Product> inCategory(Integer categoryId) {
    return (root, query, criteriaBuilder) -> {
      if (categoryId == null) {
        return criteriaBuilder.conjunction();
      }
      // Join với bảng Category để lấy id
      Join<Product, Category> categoryJoin =
          root.join("category", JoinType.INNER); // Dùng INNER JOIN
      return criteriaBuilder.equal(categoryJoin.get("id"), categoryId);
    };
  }

  // Specification: Lọc theo provinceCode
  public static Specification<Product> inProvince(String provinceCode) {
    return (root, query, criteriaBuilder) -> {
      if (!StringUtils.hasText(provinceCode)) {
        return criteriaBuilder.conjunction();
      }
      return criteriaBuilder.equal(root.get("provinceCode"), provinceCode);
    };
  }

  public static Specification<Product> hasMinPrice(BigDecimal minPrice) {
    if (minPrice == null) {
      return null;
    }
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
  }

  public static Specification<Product> hasMaxPrice(BigDecimal maxPrice) {
    if (maxPrice == null) {
      return null;
    }
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
  }

  public static Specification<Product> hasMinRating(Double minRating) {

    if (minRating == null || minRating <= 0) { // Không lọc nếu rating <= 0
      return null;
    }
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.greaterThanOrEqualTo(root.get("averageRating"), minRating);
  }

  // Specification: Lọc theo trạng thái (dùng cho Admin)
  public static Specification<Product> hasStatus(String statusString) {
    return (root, query, criteriaBuilder) -> {
      if (!StringUtils.hasText(statusString)) {
        return criteriaBuilder.conjunction();
      }
      try {
        ProductStatus status =
            ProductStatus.valueOf(statusString.toUpperCase()); // Chuyển string thành Enum
        return criteriaBuilder.equal(root.get("status"), status);
      } catch (IllegalArgumentException e) {

        return criteriaBuilder.disjunction(); // Điều kiện luôn sai
      }
    };
  }

  // Specification: Lọc theo farmerId (dùng cho Admin)
  public static Specification<Product> byFarmer(Long farmerId) {
    return (root, query, criteriaBuilder) -> {
      if (farmerId == null) {
        return criteriaBuilder.conjunction();
      }
      // Join với bảng User (farmer) để lấy id
      Join<Product, User> farmerJoin = root.join("farmer", JoinType.INNER);
      return criteriaBuilder.equal(farmerJoin.get("id"), farmerId);
    };
  }

  public static Specification<Product> inWard(String wardCode) {
    return (root, query, criteriaBuilder) -> {
      if (!StringUtils.hasText(wardCode)) {
        return criteriaBuilder.conjunction();
      }
      // Join Product -> User (farmer) -> FarmerProfile
      Join<Product, User> farmerJoin = root.join("farmer", JoinType.INNER);
      Join<User, FarmerProfile> profileJoin =
          farmerJoin.join("farmerProfile", JoinType.INNER); // INNER vì muốn lọc theo profile
      return criteriaBuilder.equal(profileJoin.get("wardCode"), wardCode);
    };
  }

  public static Specification<Product> isB2cProduct() {
    return (root, query, criteriaBuilder) -> criteriaBuilder.isFalse(root.get("b2bEnabled"));
  }

  public static Specification<Product> isB2bSupply() {
    return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("b2bEnabled"));
  }
}
