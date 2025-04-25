package com.yourcompany.agritrade.usermanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true) // Kế thừa equals/hashCode từ UserResponse
@JsonInclude(JsonInclude.Include.NON_NULL) // Không hiển thị profile null
public class UserProfileResponse extends UserResponse { // Kế thừa UserResponse
    private FarmerProfileResponse farmerProfile;
    private BusinessProfileResponse businessProfile;
    // Thêm các thông tin khác nếu cần
}