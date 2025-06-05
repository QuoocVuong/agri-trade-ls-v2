package com.yourcompany.agritrade.ordering.dto.request;

import com.google.firebase.database.annotations.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgreedOrderItemRequest {
    @NotNull
    private Long productId; // ID sản phẩm gốc (để tham chiếu)
    @NotBlank
    private String productName; // Tên sản phẩm tại thời điểm thỏa thuận
    @NotBlank
    private String unit; // Đơn vị tính thỏa thuận
    @NotNull
    @Positive
    private Integer quantity; // Số lượng thỏa thuận
    @NotNull
    private BigDecimal pricePerUnit; // Đơn giá thỏa thuận
}
