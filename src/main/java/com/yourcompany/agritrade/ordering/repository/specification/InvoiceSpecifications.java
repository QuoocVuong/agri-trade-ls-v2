package com.yourcompany.agritrade.ordering.repository.specification;

import com.yourcompany.agritrade.ordering.domain.*;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

public class InvoiceSpecifications {

  public static Specification<Invoice> hasStatus(InvoiceStatus status) {
    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
  }

  public static Specification<Invoice> hasInvoiceNumber(String invoiceNumber) {
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.like(
            criteriaBuilder.lower(root.get("invoiceNumber")),
            "%" + invoiceNumber.toLowerCase() + "%");
  }

  public static Specification<Invoice> hasOrderCode(String orderCode) {
    return (root, query, criteriaBuilder) -> {
      Join<Invoice, Order> orderJoin = root.join("order", JoinType.INNER);
      return criteriaBuilder.like(
          criteriaBuilder.lower(orderJoin.get("orderCode")), "%" + orderCode.toLowerCase() + "%");
    };
  }

  public static Specification<Invoice> hasBuyerFullName(String buyerFullName) {
    return (root, query, criteriaBuilder) -> {
      Join<Invoice, Order> orderJoin = root.join("order", JoinType.INNER);
      Join<Order, User> buyerJoin = orderJoin.join("buyer", JoinType.INNER);
      return criteriaBuilder.like(
          criteriaBuilder.lower(buyerJoin.get("fullName")),
          "%" + buyerFullName.toLowerCase() + "%");
    };
  }

  // Specification để fetch thông tin cần thiết
  public static Specification<Invoice> fetchOrderAndBuyer() {
    return (root, query, criteriaBuilder) -> {
      // Chỉ áp dụng fetch cho các query không phải là count query
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        Fetch<Invoice, Order> orderFetch = root.fetch("order", JoinType.INNER);
        orderFetch.fetch("buyer", JoinType.INNER);
      }
      return criteriaBuilder
          .conjunction(); // Luôn trả về true để không ảnh hưởng đến điều kiện where
    };
  }

  public static Specification<Invoice> isFarmerInvoice(Long farmerId) {
    return (root, query, criteriaBuilder) -> {
      Join<Invoice, Order> orderJoin = root.join("order", JoinType.INNER);
      return criteriaBuilder.equal(orderJoin.get("farmer").get("id"), farmerId);
    };
  }

  public static Specification<Invoice> isBuyerInvoice(Long buyerId) {
    return (root, query, criteriaBuilder) -> {
      Join<Invoice, Order> orderJoin = root.join("order", JoinType.INNER);
      return criteriaBuilder.equal(orderJoin.get("buyer").get("id"), buyerId);
    };
  }

  // Specification để lọc theo phương thức thanh toán của Order liên quan
  public static Specification<Invoice> hasPaymentMethod(PaymentMethod paymentMethod) {
    return (root, query, criteriaBuilder) -> {
      if (paymentMethod == null) {
        return criteriaBuilder.conjunction();
      }
      Join<Invoice, Order> orderJoin = root.join("order", JoinType.INNER);
      return criteriaBuilder.equal(orderJoin.get("paymentMethod"), paymentMethod);
    };
  }

  public static Specification<Invoice> hasOrderPaymentStatus(PaymentStatus paymentStatus) {
    return (root, query, criteriaBuilder) -> {
      if (paymentStatus == null) {
        return criteriaBuilder.conjunction();
      }
      Join<Invoice, Order> orderJoin = root.join("order", JoinType.INNER);
      return criteriaBuilder.equal(orderJoin.get("paymentStatus"), paymentStatus);
    };
  }
}
