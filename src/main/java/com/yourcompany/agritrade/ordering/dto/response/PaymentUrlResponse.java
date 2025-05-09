package com.yourcompany.agritrade.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentUrlResponse {
    private String paymentUrl;
    private String paymentMethod; // VNPAY, MOMO
    // Có thể thêm các thông tin khác nếu cần
}