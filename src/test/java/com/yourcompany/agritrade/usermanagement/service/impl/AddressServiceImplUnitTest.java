package com.yourcompany.agritrade.usermanagement.service.impl;

import com.yourcompany.agritrade.common.exception.BadRequestException;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.usermanagement.domain.Address;
import com.yourcompany.agritrade.usermanagement.domain.AddressType;
import com.yourcompany.agritrade.usermanagement.domain.User;
import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import com.yourcompany.agritrade.usermanagement.mapper.AddressMapper;
import com.yourcompany.agritrade.usermanagement.repository.AddressRepository;
import com.yourcompany.agritrade.usermanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplUnitTest {

    @Mock private AddressRepository addressRepository;
    @Mock private UserRepository userRepository;
    @Mock private AddressMapper addressMapper;
    @Mock private Authentication authentication;

    @InjectMocks
    private AddressServiceImpl addressService;

    private User currentUser;
    private AddressRequest addressRequest;
    private Address addressEntity;
    private AddressResponse addressResponseDto;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("user@example.com");
        currentUser.setFullName("Test User");
        currentUser.setPhoneNumber("0987654321");

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
        // ... set các trường khác cho addressEntity

        addressResponseDto = new AddressResponse();
        addressResponseDto.setId(10L);
        addressResponseDto.setUserId(currentUser.getId());
        addressResponseDto.setFullName(addressRequest.getFullName());
        // ... set các trường khác cho addressResponseDto

        // Mock hành vi của authentication
        when(authentication.getName()).thenReturn(currentUser.getEmail());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(userRepository.findByEmail(currentUser.getEmail())).thenReturn(Optional.of(currentUser));
    }

    @Test
    void getMyAddresses_success() {
        when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity));
        when(addressMapper.toAddressResponseList(List.of(addressEntity))).thenReturn(List.of(addressResponseDto));

        List<AddressResponse> result = addressService.getMyAddresses(authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(addressResponseDto.getFullName(), result.get(0).getFullName());
        verify(addressRepository).findByUserId(currentUser.getId());
        verify(addressMapper).toAddressResponseList(List.of(addressEntity));
    }

    @Test
    void getMyAddressById_found_success() {
        when(addressRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(addressEntity));
        when(addressMapper.toAddressResponse(addressEntity)).thenReturn(addressResponseDto);

        AddressResponse result = addressService.getMyAddressById(authentication, 10L);

        assertNotNull(result);
        assertEquals(addressResponseDto.getId(), result.getId());
        verify(addressRepository).findByIdAndUserId(10L, currentUser.getId());
    }

    @Test
    void getMyAddressById_notFound_throwsResourceNotFoundException() {
        when(addressRepository.findByIdAndUserId(99L, currentUser.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> addressService.getMyAddressById(authentication, 99L));
    }

    @Test
    void addMyAddress_asDefault_unsetsOtherDefaults() {
        addressRequest.setIsDefault(true);
        Address otherDefaultAddress = new Address();
        otherDefaultAddress.setId(11L);
        otherDefaultAddress.setUser(currentUser);
        otherDefaultAddress.setDefault(true);

        Address newAddressEntityFromMapper = new Address(); // Giả lập kết quả từ mapper
        newAddressEntityFromMapper.setUser(currentUser); // Quan trọng: user phải được set
        newAddressEntityFromMapper.setFullName(addressRequest.getFullName());

        when(addressMapper.requestToAddress(addressRequest)).thenReturn(newAddressEntityFromMapper); // Giả sử addressEntity chưa có ID
        when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(otherDefaultAddress)); // Có địa chỉ mặc định khác

        when(addressRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Giả sử địa chỉ mới được lưu thành công và có ID
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address savedAddr = invocation.getArgument(0);
            if (savedAddr.getId() == null) { // Nếu là địa chỉ mới chưa có ID
                savedAddr.setId(10L); // Gán ID giả lập
            }
            // Quan trọng: đảm bảo isDefault của địa chỉ mới được set đúng trong service
            // và được phản ánh ở đây nếu bạn muốn kiểm tra nó.
            // Trong trường hợp này, service sẽ set newAddressEntityFromMapper.setDefault(true)
            return savedAddr;
        });

        when(addressMapper.toAddressResponse(any(Address.class))).thenAnswer(invocation -> {
            Address addrToMap = invocation.getArgument(0);
            AddressResponse resp = new AddressResponse();
            resp.setId(addrToMap.getId());
            resp.setFullName(addrToMap.getFullName());
            resp.setDefault(addrToMap.isDefault());
            // ... map các trường khác ...
            return resp;
        });


        // Act
        addressService.addMyAddress(authentication, addressRequest);

        // Assert
        // 1. Verify saveAll được gọi 1 lần để unset default cho otherDefaultAddress
        ArgumentCaptor<List<Address>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(addressRepository).saveAll(listCaptor.capture());
        List<Address> unsetAddresses = listCaptor.getValue();
        assertEquals(1, unsetAddresses.size());
        assertFalse(unsetAddresses.get(0).isDefault()); // Địa chỉ cũ đã được unset default
        assertEquals(otherDefaultAddress.getId(), unsetAddresses.get(0).getId());

        // 2. Verify save được gọi 1 lần để lưu địa chỉ mới
        ArgumentCaptor<Address> newAddressCaptor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(newAddressCaptor.capture());
        Address savedNewAddress = newAddressCaptor.getValue();
        assertTrue(savedNewAddress.isDefault()); // Địa chỉ mới phải là default
        assertEquals(newAddressEntityFromMapper.getFullName(), savedNewAddress.getFullName());
    }

    @Test
    void addMyAddress_firstAddress_becomesDefault() {
        addressRequest.setIsDefault(false); // User không set làm mặc định
        when(addressMapper.requestToAddress(addressRequest)).thenReturn(addressEntity);
        when(addressRepository.findByUserId(currentUser.getId())).thenReturn(Collections.emptyList()); // Không có địa chỉ nào trước đó
        when(addressRepository.save(any(Address.class))).thenReturn(addressEntity);
        when(addressMapper.toAddressResponse(addressEntity)).thenReturn(addressResponseDto);

        addressService.addMyAddress(authentication, addressRequest);

        ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(addressCaptor.capture());
        assertTrue(addressCaptor.getValue().isDefault()); // Phải tự động thành mặc định
    }


    @Test
    void updateMyAddress_setAsDefault_success() {
        addressRequest.setIsDefault(true);
        addressEntity.setDefault(false); // Địa chỉ hiện tại không phải mặc định

        Address otherAddress = new Address(); // Một địa chỉ khác, có thể là mặc định hoặc không
        otherAddress.setId(11L);
        otherAddress.setUser(currentUser);
        otherAddress.setDefault(true);


        when(addressRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(addressEntity));
        when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity, otherAddress));
        doNothing().when(addressMapper).updateAddressFromRequest(eq(addressRequest), eq(addressEntity));
        when(addressRepository.save(any(Address.class))).thenReturn(addressEntity); // Giả lập save
        when(addressMapper.toAddressResponse(addressEntity)).thenReturn(addressResponseDto);


        addressService.updateMyAddress(authentication, 10L, addressRequest);

        verify(addressRepository).saveAll(anyList()); // Kiểm tra unsetDefaultForOtherAddresses được gọi
        assertTrue(addressEntity.isDefault());
    }

    @Test
    void updateMyAddress_unsetOnlyDefaultAddress_throwsBadRequestException() {
        addressRequest.setIsDefault(false);
        addressEntity.setDefault(true); // Đây là địa chỉ mặc định duy nhất

        when(addressRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(addressEntity));
        when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity)); // Chỉ có 1 địa chỉ
        doNothing().when(addressMapper).updateAddressFromRequest(eq(addressRequest), eq(addressEntity));


        assertThrows(BadRequestException.class, () -> {
            addressService.updateMyAddress(authentication, 10L, addressRequest);
        });
    }


    @Test
    void deleteMyAddress_defaultAddress_setsNewDefault() {
        addressEntity.setDefault(true); // Địa chỉ cần xóa là mặc định
        Address otherAddress = new Address();
        otherAddress.setId(11L);
        otherAddress.setUser(currentUser);
        otherAddress.setDefault(false);
        otherAddress.setCreatedAt(LocalDateTime.now().minusDays(1)); // Cũ hơn

        Address newestOtherAddress = new Address();
        newestOtherAddress.setId(12L);
        newestOtherAddress.setUser(currentUser);
        newestOtherAddress.setDefault(false);
        newestOtherAddress.setCreatedAt(LocalDateTime.now()); // Mới nhất


        when(addressRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(addressEntity));
        when(addressRepository.findByUserId(currentUser.getId())).thenReturn(List.of(addressEntity, otherAddress, newestOtherAddress));
        when(addressRepository.save(any(Address.class))).thenReturn(newestOtherAddress); // Giả lập save cho newDefault

        addressService.deleteMyAddress(authentication, 10L);

        verify(addressRepository).delete(addressEntity);
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture()); // newDefault được save
        assertTrue(captor.getValue().isDefault());
        assertEquals(newestOtherAddress.getId(), captor.getValue().getId());
    }

    @Test
    void deleteMyAddress_nonDefaultAddress_deletesSuccessfully() {
        addressEntity.setDefault(false);
        when(addressRepository.findByIdAndUserId(10L, currentUser.getId())).thenReturn(Optional.of(addressEntity));

        addressService.deleteMyAddress(authentication, 10L);

        verify(addressRepository).delete(addressEntity);
        verify(addressRepository, never()).saveAll(anyList()); // Không set lại default cho cái khác
        verify(addressRepository, never()).save(argThat(addr -> addr.isDefault()));
    }

    // TODO: Thêm test cho setMyDefaultAddress
}