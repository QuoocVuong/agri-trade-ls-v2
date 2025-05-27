// ordering/repository/InvoiceRepository.java
package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  Optional<Invoice> findByOrderId(Long orderId);
  Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

  // Query để lấy hóa đơn ISSUED quá hạn, fetch thêm order và buyer
  @Query("SELECT i FROM Invoice i JOIN FETCH i.order o JOIN FETCH o.buyer b WHERE i.status = :status AND i.dueDate < :date")
  List<Invoice> findWithDetailsByStatusAndDueDateBefore(@Param("status") InvoiceStatus status, @Param("date") LocalDate date);

  // Query để lấy hóa đơn ISSUED sắp đến hạn, fetch thêm order và buyer
  @Query("SELECT i FROM Invoice i JOIN FETCH i.order o JOIN FETCH o.buyer b WHERE i.status = :status AND i.dueDate BETWEEN :startDate AND :endDate")
  List<Invoice> findWithDetailsByStatusAndDueDateBetween(@Param("status") InvoiceStatus status, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

  Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);
  Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);
}