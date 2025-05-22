package com.yourcompany.agritrade.ordering.repository;

import com.yourcompany.agritrade.ordering.domain.Invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  Optional<Invoice> findByOrderId(Long orderId);

  Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

  // *** THÊM CÁC PHƯƠNG THỨC NÀY ***
  List<Invoice> findByStatusAndDueDateBefore(InvoiceStatus status, LocalDate date);
  List<Invoice> findByStatusAndDueDateBetween(InvoiceStatus status, LocalDate startDate, LocalDate endDate);

  // (Tùy chọn) Nếu cần phân trang cho Admin xem danh sách invoices
  Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);
  // Hoặc dùng Specification để lọc linh hoạt hơn
   Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);
  // *******************************
}
