package com.yourcompany.agritrade.ordering.dto.response;

import com.yourcompany.agritrade.catalog.dto.response.ProductInfoResponse;
import com.yourcompany.agritrade.ordering.domain.SupplyOrderRequestStatus;
import com.yourcompany.agritrade.usermanagement.dto.response.UserInfoSimpleResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SupplyOrderRequestResponse {
    private Long id;
    private UserInfoSimpleResponse buyer;
    private UserInfoSimpleResponse farmer;
    private ProductInfoResponse product; // Thông tin sản phẩm được yêu cầu
    private Integer requestedQuantity;
    private String requestedUnit;
    private BigDecimal proposedPricePerUnit;
    private String buyerNotes;
    private String shippingFullName;
    private String shippingPhoneNumber;
    private String shippingAddressDetail;
    // ... (các trường địa chỉ khác) ...
    private LocalDate expectedDeliveryDate;
    private SupplyOrderRequestStatus status;
    private String farmerResponseMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}