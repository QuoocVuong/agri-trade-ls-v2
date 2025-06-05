package com.yourcompany.agritrade.ordering.dto.request;

import lombok.Data;

@Data
public class OrderCalculationRequest {
  private Long shippingAddressId; // ID địa chỉ để tính phí ship

}
