// src/main/java/com/yourcompany/agritrade/ordering/mapper/InvoiceMapper.java
package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.ordering.domain.Invoice;
import com.yourcompany.agritrade.ordering.dto.response.InvoiceSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    @Mapping(target = "invoiceId", source = "id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "buyerFullName", source = "order.buyer.fullName")
    InvoiceSummaryResponse toInvoiceSummaryResponse(Invoice invoice);

    default Page<InvoiceSummaryResponse> toInvoiceSummaryResponsePage(Page<Invoice> invoicePage) {
        return invoicePage.map(this::toInvoiceSummaryResponse);
    }
}