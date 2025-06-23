package com.yourcompany.agritrade.usermanagement.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
  @Size(max = 100, message = "Full name must be less than 100 characters")
  private String fullName; // Cho phép null nếu không muốn cập nhật

  @Size(max = 20, message = "Phone number must be less than 20 characters")
  private String phoneNumber; // Cho phép null

  @Size(max = 512, message = "Avatar URL too long")
  private String avatarUrl; // Cho phép null
}
