package com.yourcompany.agritradels.usermanagement.dto.response; // Đúng package

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private Set<String> roles;
    private LocalDateTime createdAt;
}