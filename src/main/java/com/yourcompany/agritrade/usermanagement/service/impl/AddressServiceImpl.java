package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import com.yourcompany.agritrade.usermanagement.mapper.AddressMapper;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import com.yourcompany.agritrade.usermanagement.service.AddressService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressServiceImpl implements AddressService {

  private final AddressRepository addressRepository;
  private final UserRepository userRepository;
  private final AddressMapper addressMapper;

  @Override
  @Transactional(readOnly = true)
  public List<AddressResponse> getMyAddresses(Authentication authentication) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
    log.debug("Fetching addresses for user ID: {}", currentUser.getId());
    // Repo đã có @Where(clause = "is_deleted = false") nên tự lọc
    List<Address> addresses = addressRepository.findByUserId(currentUser.getId());
    return addressMapper.toAddressResponseList(addresses);
  }

  @Override
  @Transactional(readOnly = true)
  public AddressResponse getMyAddressById(Authentication authentication, Long addressId) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
    return addressMapper.toAddressResponse(address);
  }

  @Override
  @Transactional
  public AddressResponse addMyAddress(Authentication authentication, AddressRequest request) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();

    Address newAddress = addressMapper.requestToAddress(request);
    newAddress.setUser(currentUser);

    // Nếu đặt làm mặc định, bỏ mặc định của các địa chỉ khác
    if (request.getIsDefault() != null && request.getIsDefault()) {
      unsetDefaultForOtherAddresses(currentUser.getId(), null); // Bỏ mặc định tất cả
      newAddress.setDefault(true);
    } else {
      // Nếu đây là địa chỉ đầu tiên, tự động đặt làm mặc định
      if (addressRepository.findByUserId(currentUser.getId()).isEmpty()) {
        newAddress.setDefault(true);
      } else {
        newAddress.setDefault(false);
      }
    }

    Address savedAddress = addressRepository.save(newAddress);
    log.info(
        "Added new address with ID: {} for user ID: {}", savedAddress.getId(), currentUser.getId());
    return addressMapper.toAddressResponse(savedAddress);
  }

  @Override
  @Transactional
  public AddressResponse updateMyAddress(
      Authentication authentication, Long addressId, AddressRequest request) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
    Address existingAddress =
        addressRepository
            .findByIdAndUserId(addressId, currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

    // Cập nhật thông tin từ request
    addressMapper.updateAddressFromRequest(request, existingAddress);

    // Xử lý cờ mặc định
    if (request.getIsDefault() != null && request.getIsDefault() && !existingAddress.isDefault()) {
      unsetDefaultForOtherAddresses(currentUser.getId(), addressId); // Bỏ mặc định các địa chỉ khác
      existingAddress.setDefault(true);
    } else if (request.getIsDefault() != null
        && !request.getIsDefault()
        && existingAddress.isDefault()) {
      // Không cho phép bỏ mặc định nếu đây là địa chỉ duy nhất
      if (addressRepository.findByUserId(currentUser.getId()).size() <= 1) {
        throw new BadRequestException("Cannot unset default on the only address.");
      }
      existingAddress.setDefault(false);
      // Cần đảm bảo luôn có 1 địa chỉ mặc định nếu có > 1 địa chỉ? (Tùy logic)
    }

    Address updatedAddress = addressRepository.save(existingAddress);
    log.info(
        "Updated address with ID: {} for user ID: {}", updatedAddress.getId(), currentUser.getId());
    return addressMapper.toAddressResponse(updatedAddress);
  }

  @Override
  @Transactional
  public void deleteMyAddress(Authentication authentication, Long addressId) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
    Address address =
        addressRepository
            .findByIdAndUserId(addressId, currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

    // Kiểm tra nếu xóa địa chỉ mặc định và còn địa chỉ khác
    if (address.isDefault()) {
      List<Address> otherAddresses =
          addressRepository.findByUserId(currentUser.getId()).stream()
              .filter(a -> !a.getId().equals(addressId))
              .toList();
      if (!otherAddresses.isEmpty()) {
        // Đặt địa chỉ khác làm mặc định (ví dụ: cái mới nhất)
        Address newDefault =
            otherAddresses.stream()
                .max(java.util.Comparator.comparing(Address::getCreatedAt))
                .get();
        newDefault.setDefault(true);
        addressRepository.save(newDefault);
      } else {
        // Nếu đây là địa chỉ cuối cùng, không nên xóa? Hoặc cho phép xóa.
        throw new BadRequestException("Cannot delete the last address.");
      }
    }

    addressRepository.delete(address); // Thực hiện soft delete nhờ @SQLDelete
    log.info("Soft deleted address with ID: {} for user ID: {}", addressId, currentUser.getId());
  }

  @Override
  @Transactional
  public void setMyDefaultAddress(Authentication authentication, Long addressId) {
    User currentUser = SecurityUtils.getCurrentAuthenticatedUser();
    Address newDefaultAddress =
        addressRepository
            .findByIdAndUserId(addressId, currentUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

    if (!newDefaultAddress.isDefault()) {
      unsetDefaultForOtherAddresses(currentUser.getId(), addressId);
      newDefaultAddress.setDefault(true);
      addressRepository.save(newDefaultAddress);
      log.info("Set address ID: {} as default for user ID: {}", addressId, currentUser.getId());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public AddressResponse getDefaultAddressByUserId(Long userId) {
    // Kiểm tra xem user có tồn tại không (tùy chọn, nhưng nên có)
    if (!userRepository.existsById(userId)) {
      throw new ResourceNotFoundException("User", "id", userId);
    }
    // @Where trên Address entity sẽ tự lọc is_deleted = false
    return addressRepository
        .findByUserIdAndIsDefaultTrue(userId)
        .map(addressMapper::toAddressResponse)
        .orElse(null); // Trả về null nếu không có địa chỉ mặc định
  }

  private void unsetDefaultForOtherAddresses(Long userId, Long excludeAddressId) {
    List<Address> currentDefaults =
        addressRepository.findByUserId(userId).stream()
            .filter(Address::isDefault)
            .filter(
                a ->
                    excludeAddressId == null
                        || !a.getId()
                            .equals(excludeAddressId)) // Loại trừ địa chỉ đang được set/update
            .toList();
    if (!currentDefaults.isEmpty()) {
      currentDefaults.forEach(addr -> addr.setDefault(false));
      addressRepository.saveAll(currentDefaults);
    }
  }
}
