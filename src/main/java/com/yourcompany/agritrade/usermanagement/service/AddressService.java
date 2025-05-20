package com.yourcompany.agritrade.usermanagement.service;

import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface AddressService {

  /** Lấy danh sách địa chỉ của người dùng hiện tại */
  List<AddressResponse> getMyAddresses(Authentication authentication);

  /** Lấy chi tiết địa chỉ theo ID (của người dùng hiện tại) */
  AddressResponse getMyAddressById(Authentication authentication, Long addressId);

  /** Thêm địa chỉ mới cho người dùng hiện tại */
  AddressResponse addMyAddress(Authentication authentication, AddressRequest request);

  /** Cập nhật địa chỉ của người dùng hiện tại */
  AddressResponse updateMyAddress(
      Authentication authentication, Long addressId, AddressRequest request);

  /** Xóa địa chỉ của người dùng hiện tại (soft delete) */
  void deleteMyAddress(Authentication authentication, Long addressId);

  /** Đặt địa chỉ làm mặc định cho người dùng hiện tại */
  void setMyDefaultAddress(Authentication authentication, Long addressId);
}
