package com.yourcompany.agritrade.usermanagement.dto.response;
import lombok.Data;
// DTO rất cơ bản để nhúng vào các response khác
@Data
public class UserInfoSimpleResponse {
    private Long id;
    private String fullName;
    private String avatarUrl;
}