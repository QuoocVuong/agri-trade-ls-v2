package com.yourcompany.agritrade.usermanagement.dto.request;

import com.yourcompany.agritrade.usermanagement.domain.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be less than 100 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Invalid Vietnamese phone number format")
    @Size(max = 20)
    private String phoneNumber;

    @NotBlank(message = "Address detail is required")
    private String addressDetail;

    @NotBlank(message = "Province code is required")
    @Size(max = 10)
    private String provinceCode;

    @NotBlank(message = "District code is required")
    @Size(max = 10)
    private String districtCode;

    @NotBlank(message = "Ward code is required")
    @Size(max = 10)
    private String wardCode;

    @NotNull(message = "Address type is required")
    private AddressType type = AddressType.SHIPPING; // Mặc định là SHIPPING

    @NotNull(message = "Default status is required")
    private Boolean isDefault = false;
}