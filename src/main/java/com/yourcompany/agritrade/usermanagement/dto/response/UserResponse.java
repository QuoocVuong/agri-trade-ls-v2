package com.yourcompany.agritrade.usermanagement.dto.response;

import java.time.LocalDateTime;
import java.util.Set;
import lombok.Data;

@Data
public class UserResponse {
  private Long id;
  private String email;
  private String fullName;
  private String phoneNumber;
  private String avatarUrl;
  private Set<String> roles;
  private boolean isActive;
  private LocalDateTime createdAt;
}
