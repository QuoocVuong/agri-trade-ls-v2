package com.yourcompany.agritrade.ordering.service;

import com.yourcompany.agritrade.ordering.domain.Order;
import com.yourcompany.agritrade.ordering.domain.Invoice;
import java.io.ByteArrayInputStream; // Để trả về PDF

public interface InvoiceService {
    /** Tạo hoặc lấy hóa đơn cho một đơn hàng */
    Invoice getOrCreateInvoiceForOrder(Order order);

    /** Tạo file PDF cho hóa đơn */
    ByteArrayInputStream generateInvoicePdf(Long invoiceId); // Trả về stream để controller xử lý
    // Hoặc trả về byte[]
    // byte[] generateInvoicePdfBytes(Long invoiceId);
}