package com.yourcompany.agritrade.ordering.dto.request;

import lombok.Data;
@Data
public class PaymentNotificationRequest {
    private String referenceCode;
    private String notes;
}