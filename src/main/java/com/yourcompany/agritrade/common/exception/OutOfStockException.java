package com.yourcompany.agritrade.common.exception;

import lombok.Getter; // Thêm Getter cho tiện

@Getter // Thêm Lombok Getter
public class OutOfStockException extends RuntimeException {

    private final Integer availableStock; // Thêm trường này để lưu số lượng tồn
    // private final Long productId; // (Tùy chọn) Có thể thêm ID sản phẩm nếu cần

    // Sửa constructor để nhận availableStock
    public OutOfStockException(String message, Integer availableStock /*, Long productId */) {
        super(message);
        this.availableStock = availableStock;
        // this.productId = productId;
    }

}