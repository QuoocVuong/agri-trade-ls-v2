package com.yourcompany.agritrade.usermanagement.dto.response;

import com.yourcompany.agritrade.usermanagement.domain.AddressType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}
