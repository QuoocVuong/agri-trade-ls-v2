package com.yourcompany.agritradels.catalog.repository.specification;

import com.yourcompany.agritradels.catalog.domain.Category;
import com.yourcompany.agritradels.catalog.domain.Product;
import com.yourcompany.agritradels.catalog.domain.ProductStatus;
import com.yourcompany.agritradels.usermanagement.domain.User; // Import User
import jakarta.persistence.criteria.*; // Import các thành phần criteria
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class ProductSpecifications {

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
            Predicate descriptionLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern);
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
            Join<Product, Category> categoryJoin = root.join("category", JoinType.INNER); // Dùng INNER JOIN
            return criteriaBuilder.equal(categoryJoin.get("id"), categoryId);
            // Lưu ý: Lọc theo danh mục con cần logic phức tạp hơn
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

    // Specification: Lọc theo trạng thái (dùng cho Admin)
    public static Specification<Product> hasStatus(String statusString) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(statusString)) {
                return criteriaBuilder.conjunction();
            }
            try {
                ProductStatus status = ProductStatus.valueOf(statusString.toUpperCase()); // Chuyển string thành Enum
                return criteriaBuilder.equal(root.get("status"), status);
            } catch (IllegalArgumentException e) {
                // Nếu statusString không hợp lệ, trả về điều kiện không khớp với bản ghi nào
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

    // (Optional) Specification: Lọc sản phẩm chưa bị xóa mềm (nếu không dùng @Where)
     /*
     public static Specification<Product> isNotDeleted() {
         return (root, query, criteriaBuilder) ->
                 criteriaBuilder.isFalse(root.get("isDeleted"));
     }
     */
}