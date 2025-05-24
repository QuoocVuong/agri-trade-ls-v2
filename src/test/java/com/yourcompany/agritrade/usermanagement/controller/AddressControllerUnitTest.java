package com.yourcompany.agritrade.usermanagement.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.exception.ResourceNotFoundException;
import com.yourcompany.agritrade.usermanagement.domain.AddressType;
import com.yourcompany.agritrade.usermanagement.dto.request.AddressRequest;
import com.yourcompany.agritrade.usermanagement.dto.response.AddressResponse;
import com.yourcompany.agritrade.usermanagement.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressControllerUnitTest {

    @Mock private AddressService addressService;
    @Mock private Authentication authentication;

    @InjectMocks
    private AddressController addressController;

    private AddressRequest addressRequest;
    private AddressResponse addressResponseDto;
    private List<AddressResponse> addressResponseList;

    @BeforeEach
    void setUp() {
        addressRequest = new AddressRequest();
        addressRequest.setFullName("Test Recipient");
        addressRequest.setPhoneNumber("0123456789");
        addressRequest.setAddressDetail("123 Test St");
        addressRequest.setProvinceCode("PC");
        addressRequest.setDistrictCode("DC");
        addressRequest.setWardCode("WC");
        addressRequest.setType(AddressType.SHIPPING);
        addressRequest.setIsDefault(true);

        addressResponseDto = new AddressResponse();
        addressResponseDto.setId(1L);
        addressResponseDto.setFullName("Test Recipient");
        addressResponseDto.setPhoneNumber("0123456789");
        // ... set other fields

        addressResponseList = Collections.singletonList(addressResponseDto);
    }

    @Test
    void getMyAddresses_success_returnsOkWithAddressList() {
        when(addressService.getMyAddresses(authentication)).thenReturn(addressResponseList);

        ResponseEntity<ApiResponse<List<AddressResponse>>> responseEntity = addressController.getMyAddresses(authentication);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(addressResponseList, responseEntity.getBody().getData());
        verify(addressService).getMyAddresses(authentication);
    }

    @Test
    void getMyAddressById_found_returnsOkWithAddress() {
        when(addressService.getMyAddressById(authentication, 1L)).thenReturn(addressResponseDto);

        ResponseEntity<ApiResponse<AddressResponse>> responseEntity = addressController.getMyAddressById(authentication, 1L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(addressResponseDto, responseEntity.getBody().getData());
    }

    @Test
    void getMyAddressById_notFound_throwsResourceNotFound_handledByGlobalHandler() {
        // Giả sử GlobalExceptionHandler sẽ bắt ResourceNotFoundException và trả về 404
        // Unit test controller thường không test GlobalExceptionHandler
        // Chúng ta chỉ cần kiểm tra service được gọi
        when(addressService.getMyAddressById(authentication, 99L)).thenThrow(new ResourceNotFoundException("Address", "id", 99L));

        assertThrows(ResourceNotFoundException.class, () -> {
            addressController.getMyAddressById(authentication, 99L);
        });
        verify(addressService).getMyAddressById(authentication, 99L);
    }

    @Test
    void addMyAddress_success_returnsCreatedWithAddress() {
        when(addressService.addMyAddress(authentication, addressRequest)).thenReturn(addressResponseDto);

        ResponseEntity<ApiResponse<AddressResponse>> responseEntity = addressController.addMyAddress(authentication, addressRequest);

        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(addressResponseDto, responseEntity.getBody().getData());
        assertEquals("Address added successfully", responseEntity.getBody().getMessage());
    }

    @Test
    void updateMyAddress_success_returnsOkWithUpdatedAddress() {
        when(addressService.updateMyAddress(authentication, 1L, addressRequest)).thenReturn(addressResponseDto);

        ResponseEntity<ApiResponse<AddressResponse>> responseEntity = addressController.updateMyAddress(authentication, 1L, addressRequest);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals(addressResponseDto, responseEntity.getBody().getData());
        assertEquals("Address updated successfully", responseEntity.getBody().getMessage());
    }

    @Test
    void deleteMyAddress_success_returnsOk() {
        doNothing().when(addressService).deleteMyAddress(authentication, 1L);

        ResponseEntity<ApiResponse<Void>> responseEntity = addressController.deleteMyAddress(authentication, 1L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Address deleted successfully", responseEntity.getBody().getMessage());
        verify(addressService).deleteMyAddress(authentication, 1L);
    }

    @Test
    void setMyDefaultAddress_success_returnsOk() {
        doNothing().when(addressService).setMyDefaultAddress(authentication, 1L);

        ResponseEntity<ApiResponse<Void>> responseEntity = addressController.setMyDefaultAddress(authentication, 1L);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertTrue(responseEntity.getBody().isSuccess());
        assertEquals("Address set as default successfully", responseEntity.getBody().getMessage());
        verify(addressService).setMyDefaultAddress(authentication, 1L);
    }
}