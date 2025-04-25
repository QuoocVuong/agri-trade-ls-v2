package com.yourcompany.agritrade.usermanagement.dto.response; // Đúng package

import lombok.Data;

@Data
public class LoginResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private UserResponse user;

    public LoginResponse(String accessToken, UserResponse user) {
        this.accessToken = accessToken;
        this.user = user;
    }
}