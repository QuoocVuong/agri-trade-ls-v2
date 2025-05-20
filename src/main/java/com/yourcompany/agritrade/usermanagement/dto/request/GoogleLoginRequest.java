package com.yourcompany.agritrade.usermanagement.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// GoogleLoginRequest.java
@Data
public class GoogleLoginRequest {
  @NotBlank private String idToken; // ID Token nhận từ Google ở Frontend
}
