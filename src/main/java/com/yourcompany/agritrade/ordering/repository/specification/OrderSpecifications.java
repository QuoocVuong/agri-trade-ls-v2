package com.yourcompany.agritrade.ordering.repository.specification;

import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.OrderStatus;
import com.yourcompany.agritrade.usermanagement.domain.FarmerProfile;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.criteria.*; // Import các thành phần criteria
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils; // Import StringUtils

public class OrderSpecifications {

    /** Specification: Lọc theo trạng thái đơn hàng */
    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction(); // Không lọc nếu status là null
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /** Specification: Lọc theo trạng thái đơn hàng (dùng String để linh hoạt hơn từ controller) */
    public static Specification<Order> hasStatus(String statusString) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(statusString)) {
                return criteriaBuilder.conjunction();
            }
            try {
                OrderStatus status = OrderStatus.valueOf(statusString.toUpperCase());
                return criteriaBuilder.equal(root.get("status"), status);
            } catch (IllegalArgumentException e) {
                // Nếu status không hợp lệ, trả về điều kiện không khớp
                return criteriaBuilder.disjunction();
            }
        };
    }


    /** Specification: Lọc theo ID người mua */
    public static Specification<Order> byBuyer(Long buyerId) {
        return (root, query, criteriaBuilder) -> {
            if (buyerId == null) {
                return criteriaBuilder.conjunction();
            }
            // Join với bảng User (buyer) để lấy id
            Join<Order, User> buyerJoin = root.join("buyer", JoinType.INNER); // Dùng INNER JOIN
            return criteriaBuilder.equal(buyerJoin.get("id"), buyerId);
        };
    }

    /** Specification: Lọc theo ID người bán (farmer) */
    public static Specification<Order> byFarmer(Long farmerId) {
        return (root, query, criteriaBuilder) -> {
            if (farmerId == null) {
                return criteriaBuilder.conjunction();
            }
            // Join với bảng User (farmer) để lấy id
            Join<Order, User> farmerJoin = root.join("farmer", JoinType.INNER);
            return criteriaBuilder.equal(farmerJoin.get("id"), farmerId);
        };
    }

    public static Specification<Order> hasOrderCode(String orderCode) {
        if (!StringUtils.hasText(orderCode)) {
            return null;
        }
        String codePattern = "%" + orderCode.toLowerCase() + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("orderCode")), codePattern);
    }

    public static Specification<Order> hasBuyerName(String buyerName) {
        if (!StringUtils.hasText(buyerName)) {
            return null;
        }
        String namePattern = "%" + buyerName.toLowerCase() + "%";
        return (root, query, criteriaBuilder) -> {
            Join<Order, User> buyerJoin = root.join("buyer", JoinType.LEFT); // LEFT JOIN để không loại bỏ đơn nếu buyer bị null (dù không nên)
            return criteriaBuilder.like(criteriaBuilder.lower(buyerJoin.get("fullName")), namePattern);
        };
    }

    // (Tùy chọn) Specification để fetch thông tin cho Summary
    public static Specification<Order> fetchBuyerAndFarmerSummary() {
        return (root, query, criteriaBuilder) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("buyer", JoinType.LEFT);
                root.fetch("farmer", JoinType.LEFT).fetch("farmerProfile", JoinType.LEFT);
            }
            return criteriaBuilder.conjunction();
        };
    }

    public static Specification<Order> hasFarmerName(String farmerName) {
        if (!StringUtils.hasText(farmerName)) {
            return null;
        }
        String namePattern = "%" + farmerName.toLowerCase() + "%";
        return (root, query, criteriaBuilder) -> {
            Join<Order, User> farmerJoin = root.join("farmer", JoinType.LEFT);
            // Giả sử User entity có fullName, hoặc FarmerProfile có farmName
            // Ưu tiên tìm theo farmName nếu có, sau đó là fullName của farmer
            Join<User, FarmerProfile> profileJoin = farmerJoin.join("farmerProfile", JoinType.LEFT); // Giả sử User có quan hệ tới FarmerProfile
            Predicate farmNameLike = criteriaBuilder.like(criteriaBuilder.lower(profileJoin.get("farmName")), namePattern);
            Predicate farmerFullNameLike = criteriaBuilder.like(criteriaBuilder.lower(farmerJoin.get("fullName")), namePattern);
            return criteriaBuilder.or(farmNameLike, farmerFullNameLike);
        };
    }

    // Có thể thêm các Specification khác (lọc theo ngày tạo, tổng tiền, mã đơn hàng...)
}