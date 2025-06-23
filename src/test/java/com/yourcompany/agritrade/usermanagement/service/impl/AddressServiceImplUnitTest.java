package com.yourcompany.agritrade.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.common.util.SecurityUtils;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.AddressType;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import com.yourcompany.agritrade.usermanagement.mapper.AddressMapper;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplUnitTest {

  @Mock private AddressRepository addressRepository;
  @Mock private UserRepository userRepository;
  @Mock private AddressMapper addressMapper;
  @Mock private Authentication authentication;

  // SỬA LỖI: Thêm MockedStatic để quản lý mock cho lớp tiện ích SecurityUtils
  private MockedStatic<SecurityUtils> mockedSecurityUtils;

  @InjectMocks private AddressServiceImpl addressService;

  private User currentUser;
  private AddressRequest addressRequest;
  private Address addressEntity;
  private AddressResponse addressResponseDto;

  @BeforeEach
  void setUp() {
    // SỬA LỖI: Khởi tạo mock static cho SecurityUtils trước mỗi test
    mockedSecurityUtils = Mockito.mockStatic(SecurityUtils.class);

    currentUser = new User();
    currentUser.setId(1L);
    currentUser.setEmail("user@example.com");
    currentUser.setFullName("Test User");
    currentUser.setPhoneNumber("0987654321");

    // SỬA LỖI: Định nghĩa hành vi mặc định cho SecurityUtils trong setUp
    // vì tất cả các phương thức trong service đều gọi nó.
    mockedSecurityUtils.when(SecurityUtils::getCurrentAuthenticatedUser).thenReturn(currentUser);

    addressRequest = new AddressRequest();
    addressRequest.setFullName("Recipient Name");
    addressRequest.setPhoneNumber("0123456789");
    addressRequest.setAddressDetail("123 Main St");
    addressRequest.setProvinceCode("PC");
    addressRequest.setDistrictCode("DC");
    addressRequest.setWardCode("WC");
    addressRequest.setType(AddressType.SHIPPING);
    addressRequest.setIsDefault(false);

    addressEntity = new Address();
    addressEntity.setId(10L);
    addressEntity.setUser(currentUser);
    addressEntity.setFullName(addressRequest.getFullName());
    addressEntity.setCreatedAt(LocalDateTime.now());

    addressResponseDto = new AddressResponse();
    addressResponseDto.setId(10L);
    addressResponseDto.setUserId(currentUser.getId());
    addressResponseDto.setFullName(addressRequest.getFullName());
  }

  // SỬA LỖI: Thêm tearDown để đóng mock static sau mỗi test
  @AfterEach
  void tearDown() {
    mockedSecurityUtils.close();
  }

  @Test
  void getMyAddresses_success() {
    when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity));
    when(addressMapper.toAddressResponseList(List.of(addressEntity)))
        .thenReturn(List.of(addressResponseDto));

    List<AddressResponse> result = addressService.getMyAddresses(authentication);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(addressResponseDto.getFullName(), result.get(0).getFullName());
    verify(addressRepository).findByUserId(currentUser.getId());
    verify(addressMapper).toAddressResponseList(List.of(addressEntity));
  }

  @Test
  void getMyAddressById_found_success() {
    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    when(addressMapper.toAddressResponse(addressEntity)).thenReturn(addressResponseDto);

    AddressResponse result = addressService.getMyAddressById(authentication, 10L);

    assertNotNull(result);
    assertEquals(addressResponseDto.getId(), result.getId());
    verify(addressRepository).findByIdAndUserId(10L, currentUser.getId());
  }

  @Test
  void getMyAddressById_notFound_throwsResourceNotFoundException() {
    when(addressRepository.findByIdAndUserId(99L, currentUser.getId()))
        .thenReturn(Optional.empty());
    assertThrows(
        ResourceNotFoundException.class,
        () -> addressService.getMyAddressById(authentication, 99L));
  }

  @Test
  void addMyAddress_asDefault_unsetsOtherDefaults() {
    addressRequest.setIsDefault(true);
    Address otherDefaultAddress = new Address();
    otherDefaultAddress.setId(11L);
    otherDefaultAddress.setUser(currentUser);
    otherDefaultAddress.setDefault(true);

    Address newAddressEntityFromMapper = new Address();
    newAddressEntityFromMapper.setUser(currentUser);
    newAddressEntityFromMapper.setFullName(addressRequest.getFullName());

    when(addressMapper.requestToAddress(addressRequest)).thenReturn(newAddressEntityFromMapper);
    // Khi gọi unsetDefaultForOtherAddresses, nó sẽ gọi findByUserId
    when(addressRepository.findByUserId(currentUser.getId()))
        .thenReturn(List.of(otherDefaultAddress));
    when(addressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    when(addressRepository.save(any(Address.class)))
        .thenAnswer(
            invocation -> {
              Address savedAddr = invocation.getArgument(0);
              if (savedAddr.getId() == null) {
                savedAddr.setId(10L);
              }
              return savedAddr;
            });

    addressService.addMyAddress(authentication, addressRequest);

    ArgumentCaptor<List<Address>> listCaptor = ArgumentCaptor.forClass(List.class);
    verify(addressRepository).saveAll(listCaptor.capture());
    List<Address> unsetAddresses = listCaptor.getValue();
    assertEquals(1, unsetAddresses.size());
    assertFalse(unsetAddresses.get(0).isDefault());

    ArgumentCaptor<Address> newAddressCaptor = ArgumentCaptor.forClass(Address.class);
    verify(addressRepository).save(newAddressCaptor.capture());
    assertTrue(newAddressCaptor.getValue().isDefault());
  }

  @Test
  void addMyAddress_firstAddress_becomesDefault() {
    addressRequest.setIsDefault(false);
    Address newAddress = new Address(); // Tạo đối tượng mới để không bị ảnh hưởng bởi test khác
    when(addressMapper.requestToAddress(addressRequest)).thenReturn(newAddress);
    when(addressRepository.findByUserId(currentUser.getId())).thenReturn(Collections.emptyList());
    when(addressRepository.save(any(Address.class))).thenReturn(newAddress);

    addressService.addMyAddress(authentication, addressRequest);

    ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
    verify(addressRepository).save(addressCaptor.capture());
    assertTrue(addressCaptor.getValue().isDefault());
  }

  @Test
  void updateMyAddress_setAsDefault_success() {
    addressRequest.setIsDefault(true);
    addressEntity.setDefault(false);

    Address otherAddress = new Address();
    otherAddress.setId(11L);
    otherAddress.setUser(currentUser);
    otherAddress.setDefault(true);

    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    // Giả lập danh sách địa chỉ cho hàm unset
    when(addressRepository.findByUserId(currentUser.getId()))
        .thenReturn(List.of(addressEntity, otherAddress));
    doNothing().when(addressMapper).updateAddressFromRequest(eq(addressRequest), eq(addressEntity));
    when(addressRepository.save(any(Address.class))).thenReturn(addressEntity);

    addressService.updateMyAddress(authentication, 10L, addressRequest);

    verify(addressRepository).saveAll(anyList());
    assertTrue(addressEntity.isDefault());
    verify(addressRepository).save(addressEntity);
  }

  @Test
  void updateMyAddress_unsetOnlyDefaultAddress_throwsBadRequestException() {
    addressRequest.setIsDefault(false);
    addressEntity.setDefault(true);

    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity));
    doNothing().when(addressMapper).updateAddressFromRequest(eq(addressRequest), eq(addressEntity));

    assertThrows(
        BadRequestException.class,
        () -> addressService.updateMyAddress(authentication, 10L, addressRequest));
  }

  @Test
  void deleteMyAddress_defaultAddress_setsNewDefault() {
    addressEntity.setDefault(true);
    Address otherAddress = new Address();
    otherAddress.setId(11L);
    otherAddress.setUser(currentUser);
    otherAddress.setDefault(false);
    otherAddress.setCreatedAt(LocalDateTime.now().minusDays(1));

    Address newestOtherAddress = new Address();
    newestOtherAddress.setId(12L);
    newestOtherAddress.setUser(currentUser);
    newestOtherAddress.setDefault(false);
    newestOtherAddress.setCreatedAt(LocalDateTime.now());

    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    // Giả lập danh sách địa chỉ cho hàm tìm địa chỉ mới
    when(addressRepository.findByUserId(currentUser.getId()))
        .thenReturn(List.of(addressEntity, otherAddress, newestOtherAddress));

    addressService.deleteMyAddress(authentication, 10L);

    verify(addressRepository).delete(addressEntity);
    ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
    // Phải là save chứ không phải saveAll
    verify(addressRepository).save(captor.capture());
    assertTrue(captor.getValue().isDefault());
    assertEquals(newestOtherAddress.getId(), captor.getValue().getId());
  }

  @Test
  void deleteMyAddress_lastAddress_throwsBadRequestException() {
    addressEntity.setDefault(true);
    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    // Giả lập đây là địa chỉ duy nhất
    when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity));

    assertThrows(
        BadRequestException.class, () -> addressService.deleteMyAddress(authentication, 10L));
  }

  @Test
  void deleteMyAddress_nonDefaultAddress_deletesSuccessfully() {
    addressEntity.setDefault(false);
    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));

    addressService.deleteMyAddress(authentication, 10L);

    verify(addressRepository).delete(addressEntity);
    verify(addressRepository, never()).save(any());
    verify(addressRepository, never()).saveAll(any());
  }

  @Test
  void setMyDefaultAddress_success() {
    addressEntity.setDefault(false);
    Address oldDefault = new Address();
    oldDefault.setId(11L);
    oldDefault.setDefault(true);

    when(addressRepository.findByIdAndUserId(10L, currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    when(addressRepository.findByUserId(currentUser.getId()))
        .thenReturn(List.of(addressEntity, oldDefault));

    addressService.setMyDefaultAddress(authentication, 10L);

    ArgumentCaptor<List<Address>> listCaptor = ArgumentCaptor.forClass(List.class);
    verify(addressRepository).saveAll(listCaptor.capture());
    assertFalse(listCaptor.getValue().get(0).isDefault()); // Old default is unset

    verify(addressRepository).save(addressEntity);
    assertTrue(addressEntity.isDefault()); // New address is set to default
  }

  @Test
  void getDefaultAddressByUserId_found() {
    addressEntity.setDefault(true);
    when(userRepository.existsById(currentUser.getId())).thenReturn(true);
    when(addressRepository.findByUserIdAndIsDefaultTrue(currentUser.getId()))
        .thenReturn(Optional.of(addressEntity));
    when(addressMapper.toAddressResponse(addressEntity)).thenReturn(addressResponseDto);

    AddressResponse result = addressService.getDefaultAddressByUserId(currentUser.getId());

    assertNotNull(result);
    assertEquals(addressResponseDto.getId(), result.getId());
  }

  @Test
  void getDefaultAddressByUserId_userNotFound_throwsResourceNotFoundException() {
    when(userRepository.existsById(99L)).thenReturn(false);
    assertThrows(
        ResourceNotFoundException.class, () -> addressService.getDefaultAddressByUserId(99L));
  }
}
