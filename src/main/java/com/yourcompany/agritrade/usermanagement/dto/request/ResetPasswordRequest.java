package com.yourcompany.agritrade.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
  @NotBlank(message = "Token is required")
  private String token;

  @NotBlank(message = "New password is required")
  @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
  private String newPassword;
}
