package com.yourcompany.agritrade.usermanagement.dto.response;

import com.yourcompany.agritrade.usermanagement.domain.AddressType; // Import Enum AddressType
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data // Bao gồm @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor
@NoArgsConstructor // Constructor không tham số
@AllArgsConstructor // Constructor với tất cả tham số
public class AddressResponse {

    private Long id;
    private Long userId; // ID của người dùng sở hữu địa chỉ này
    private String fullName;
    private String phoneNumber;
    private String addressDetail;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private AddressType type; // Sử dụng Enum đã tạo
    private boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Bạn có thể thêm các trường khác nếu cần, ví dụ: tên tỉnh/huyện/xã đầy đủ
    // private String provinceName;
    // private String districtName;
    // private String wardName;
    // (Việc lấy tên đầy đủ này thường được xử lý ở Frontend dựa trên code hoặc ở Backend nếu có logic phức tạp)
}