package com.yourcompany.agritrade.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartValidationResponse {
    private boolean isValid; // Giỏ hàng có hợp lệ để checkout không
    private List<String> messages; // Danh sách các thông báo (ví dụ: sản phẩm X đã hết hàng)
    private List<CartAdjustmentInfo> adjustments; // Chi tiết các điều chỉnh (mới)
}