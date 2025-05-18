// src/main/java/com/yourcompany/agritrade/ordering/dto/response/BankTransferInfoResponse.java
package com.yourcompany.agritrade.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankTransferInfoResponse {
    private String accountName;        // Tên chủ tài khoản Sàn
    private String accountNumber;      // Số tài khoản Sàn
    private String bankNameDisplay;    // Tên ngân hàng Sàn (để hiển thị)
    // private String bankBin;         // Mã BIN ngân hàng (đã dùng để tạo qrString, có thể không cần trả về nữa)
    private BigDecimal amount;          // Số tiền cần chuyển
    private String orderCode;           // Mã đơn hàng
    private String transferContent;     // Nội dung chuyển khoản gợi ý (thường là orderCode)
    private String qrCodeDataString;    // Chuỗi dữ liệu để frontend tự render QR code
    // Hoặc đây có thể là URL đến ảnh QR nếu backend tạo sẵn
}