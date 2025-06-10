package com.yourcompany.agritrade.ordering.repository.specification;

import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequest;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import org.springframework.data.jpa.domain.Specification;

public class SupplyOrderRequestSpecifications {

    public static Specification<SupplyOrderRequest> hasStatus(SupplyOrderRequestStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction(); // Không lọc nếu status là null
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    public static Specification<SupplyOrderRequest> forBuyer(Long buyerId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("buyer").get("id"), buyerId);
    }

    public static Specification<SupplyOrderRequest> forFarmer(Long farmerId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("farmer").get("id"), farmerId);
    }
}