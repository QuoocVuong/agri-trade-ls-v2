package com.yourcompany.agritrade.usermanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken; // Trường chứa JWT
    private String tokenType = "Bearer"; // Loại token (thường là Bearer)

    // Constructor chỉ nhận accessToken, tokenType mặc định là Bearer
    public AuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    // Có thể thêm các trường khác nếu cần:
    // private Long expiresIn; // Thời gian hết hạn (giây hoặc mili giây)
    // private String refreshToken;
    // private UserResponse user; // Thông tin user cơ bản
}