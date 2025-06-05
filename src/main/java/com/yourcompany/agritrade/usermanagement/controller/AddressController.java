package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import com.yourcompany.agritrade.usermanagement.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/addresses") // Base path cho API địa chỉ
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu xác thực cho tất cả API trong controller này
public class AddressController {

  private final AddressService addressService;

  // ===== ENDPOINT ĐỂ LẤY ĐỊA CHỈ CỦA TÔI =====
  @GetMapping("/my")
  public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses(
      Authentication authentication) {
    List<AddressResponse> addresses = addressService.getMyAddresses(authentication);
    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  // ==========================================

  @GetMapping("/my/{id}")
  public ResponseEntity<ApiResponse<AddressResponse>> getMyAddressById(
      Authentication authentication, @PathVariable Long id) {
    AddressResponse address = addressService.getMyAddressById(authentication, id);
    return ResponseEntity.ok(ApiResponse.success(address));
  }

  @PostMapping("my")
  public ResponseEntity<ApiResponse<AddressResponse>> addMyAddress(
      Authentication authentication, @Valid @RequestBody AddressRequest request) {
    AddressResponse newAddress = addressService.addMyAddress(authentication, request);
    // Trả về 201 Created và thông tin địa chỉ mới
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(newAddress, "Address added successfully"));
  }

  @PutMapping("/my/{id}")
  public ResponseEntity<ApiResponse<AddressResponse>> updateMyAddress(
      Authentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody AddressRequest request) {
    AddressResponse updatedAddress = addressService.updateMyAddress(authentication, id, request);
    return ResponseEntity.ok(ApiResponse.success(updatedAddress, "Address updated successfully"));
  }

  @DeleteMapping("/my/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteMyAddress(
      Authentication authentication, @PathVariable Long id) {
    addressService.deleteMyAddress(authentication, id);
    return ResponseEntity.ok(
        ApiResponse.success("Address deleted successfully")); // Hoặc trả về 204 No Content
  }

  @PutMapping("/my/{id}/default")
  public ResponseEntity<ApiResponse<Void>> setMyDefaultAddress(
      Authentication authentication, @PathVariable Long id) {
    addressService.setMyDefaultAddress(authentication, id);
    return ResponseEntity.ok(ApiResponse.success("Address set as default successfully"));
  }

  @GetMapping("/user/{userId}/default")
  public ResponseEntity<ApiResponse<AddressResponse>> getDefaultAddressForUser(
          @PathVariable Long userId, Authentication authentication) { // Thêm Authentication nếu cần cho kiểm tra quyền
    AddressResponse defaultAddress = addressService.getDefaultAddressByUserId(userId);
    if (defaultAddress != null) {
      return ResponseEntity.ok(ApiResponse.success(defaultAddress));
    } else {
      // Trả về 200 OK với data là null nếu không có địa chỉ mặc định,
      // hoặc 404 Not Found tùy theo logic bạn muốn.
      // Trả về 200 với data null thường dễ xử lý ở frontend hơn.
      return ResponseEntity.ok(ApiResponse.success(null, "No default address found for this user."));
    }
  }
}
