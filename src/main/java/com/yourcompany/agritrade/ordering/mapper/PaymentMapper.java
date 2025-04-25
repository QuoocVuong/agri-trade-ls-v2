package com.yourcompany.agritrade.ordering.mapper;

import com.yourcompany.agritrade.ordering.domain.Payment;
import com.yourcompany.agritrade.ordering.dto.response.PaymentResponse;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toPaymentResponse(Payment payment);
    List<PaymentResponse> toPaymentResponseList(List<Payment> payments);
}