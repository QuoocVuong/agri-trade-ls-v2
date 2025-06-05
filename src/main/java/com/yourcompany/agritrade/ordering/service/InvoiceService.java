package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.domain.InvoiceStatus;
import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.dto.response.InvoiceSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;

public interface InvoiceService {
  /** Tạo hoặc lấy hóa đơn cho một đơn hàng */
  Invoice getOrCreateInvoiceForOrder(Order order);

  /** Tạo file PDF cho hóa đơn */
  ByteArrayInputStream generateInvoicePdf(Long invoiceId); // Trả về stream để controller xử lý


  // -- Phương Thức của Admin--

  Page<InvoiceSummaryResponse> getAllInvoices(InvoiceStatus status, String keyword, Pageable pageable);

}
