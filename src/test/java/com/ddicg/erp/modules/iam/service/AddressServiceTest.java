package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.service.AddressResolutionService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.dto.request.CreateAddressRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateAddressRequest;
import com.ddicg.erp.modules.iam.dto.response.AddressResponse;
import com.ddicg.erp.modules.iam.model.Address;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.AddressRepository;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityUtil securityUtil;

    @Mock
    private AddressResolutionService addressResolutionService;

    @InjectMocks
    private AddressService addressService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .email("test@example.com")
                .fullName("Nguyễn Văn A")
                .build();
        mockUser.setId(1L);

        lenient().when(securityUtil.getCurrentUsername()).thenReturn("test@example.com");
        lenient().when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
    }

    @Test
    @DisplayName("Tạo địa chỉ với chuỗi rác ('ádasdsadsa') -> Phải ném BusinessException và KHÔNG được lưu DB")
    void createAddress_withGibberishAddress_shouldThrowException() {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .address("ádasdsadsa")
                .phoneNumber("0901234567")
                .recipientName("Test User")
                .isDefault(true)
                .build();

        when(addressResolutionService.resolve("ádasdsadsa")).thenReturn(
                ResolvedAddress.builder()
                        .success(false)
                        .rawAddress("ádasdsadsa")
                        .error("Location not found")
                        .build()
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> addressService.createAddress(request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Location not found"));

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo địa chỉ với tọa độ ngoài lãnh thổ Việt Nam -> Phải ném BusinessException")
    void createAddress_withOutsideVietnamCoordinates_shouldThrowException() {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .address("Paris, France")
                .latitude(48.8566)
                .longitude(2.3522)
                .phoneNumber("0901234567")
                .recipientName("Test User")
                .build();

        when(addressResolutionService.isInsideVietnam(48.8566, 2.3522)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> addressService.createAddress(request));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Tọa độ nằm ngoài lãnh thổ Việt Nam"));

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo địa chỉ hợp lệ tại Việt Nam -> Phải tự động phân giải tọa độ và lưu thành công")
    void createAddress_withValidVietnamAddress_shouldResolveAndSave() {
        CreateAddressRequest request = CreateAddressRequest.builder()
                .address("Số 10 Hoàng Diệu, Điện Biên, Ba Đình, Hà Nội")
                .phoneNumber("0901234567")
                .recipientName("Test User")
                .isDefault(true)
                .build();

        when(addressResolutionService.resolve("Số 10 Hoàng Diệu, Điện Biên, Ba Đình, Hà Nội")).thenReturn(
                ResolvedAddress.builder()
                        .success(true)
                        .latitude(21.0354376)
                        .longitude(105.8394163)
                        .formattedAddress("10 Hoàng Diệu, Phường Điện Biên, Ba Đình, Hà Nội, Việt Nam")
                        .build()
        );

        when(addressRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address a = invocation.getArgument(0);
            a.setId(100L);
            return a;
        });

        Response<AddressResponse> response = addressService.createAddress(request);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("10 Hoàng Diệu, Phường Điện Biên, Ba Đình, Hà Nội, Việt Nam", response.getData().getAddress());
        assertEquals(21.0354376, response.getData().getLatitude());
        assertEquals(105.8394163, response.getData().getLongitude());

        verify(addressRepository, times(1)).save(any(Address.class));
    }

    @Test
    @DisplayName("Cập nhật địa chỉ với chuỗi rác -> Phải ném BusinessException và không cập nhật DB")
    void updateAddress_withGibberishAddress_shouldThrowException() {
        Address existingAddress = Address.builder()
                .address("10 Hoàng Diệu, Hà Nội")
                .latitude(21.0354)
                .longitude(105.8394)
                .user(mockUser)
                .build();

        when(addressRepository.findBySkuAndUserId("ADDR-123", 1L)).thenReturn(Optional.of(existingAddress));
        when(addressResolutionService.resolve("ádasdsadsa")).thenReturn(
                ResolvedAddress.builder()
                        .success(false)
                        .error("Location not found")
                        .build()
        );

        UpdateAddressRequest updateRequest = UpdateAddressRequest.builder()
                .address("ádasdsadsa")
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> addressService.updateAddress("ADDR-123", updateRequest));
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());

        verify(addressRepository, never()).save(existingAddress);
    }
}
