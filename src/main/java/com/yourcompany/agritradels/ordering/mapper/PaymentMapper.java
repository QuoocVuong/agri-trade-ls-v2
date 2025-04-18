package com.yourcompany.agritradels.ordering.mapper;

import com.yourcompany.agritradels.ordering.domain.Payment;
import com.yourcompany.agritradels.ordering.dto.response.PaymentResponse;
import org.mapstruct.Mapper;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toPaymentResponse(Payment payment);
    List<PaymentResponse> toPaymentResponseList(List<Payment> payments);
}