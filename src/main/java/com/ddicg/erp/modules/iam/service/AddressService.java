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
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressService {

    AddressRepository addressRepository;
    UserRepository userRepository;
    SecurityUtil securityUtil;
    AddressResolutionService addressResolutionService;

    private record ValidatedGeoLocation(String formattedAddress, Double latitude, Double longitude) {}

    private ValidatedGeoLocation checkAndResolveAddress(String rawAddress, Double latitude, Double longitude) {
        if (rawAddress == null || rawAddress.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Địa chỉ không được để trống.");
        }

        if (latitude != null && longitude != null) {
            if (!addressResolutionService.isInsideVietnam(latitude, longitude)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tọa độ nằm ngoài lãnh thổ Việt Nam.");
            }
            return new ValidatedGeoLocation(rawAddress.trim(), latitude, longitude);
        }

        ResolvedAddress resolved = addressResolutionService.resolve(rawAddress);
        if (!resolved.isSuccess() || resolved.getLatitude() == null || resolved.getLongitude() == null) {
            String errorMsg = resolved.getError() != null ? resolved.getError() : "Địa chỉ không hợp lệ hoặc không xác định được tại Việt Nam.";
            throw new BusinessException(ErrorCode.INVALID_REQUEST, errorMsg);
        }

        return new ValidatedGeoLocation(resolved.getFormattedAddress(), resolved.getLatitude(), resolved.getLongitude());
    }

    @Transactional
    public Response<AddressResponse> createAddress(CreateAddressRequest request) {
        User currentUser = getCurrentAuthenticatedUser();

        ValidatedGeoLocation geo = checkAndResolveAddress(request.getAddress(), request.getLatitude(), request.getLongitude());

        List<Address> userAddresses = addressRepository.findByUserId(currentUser.getId());
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || userAddresses.isEmpty();

        if (makeDefault) {
            unmarkCurrentDefaultAddress(currentUser.getId());
        }

        Address address = Address.builder()
                .address(geo.formattedAddress())
                .latitude(geo.latitude())
                .longitude(geo.longitude())
                .phoneNumber(request.getPhoneNumber())
                .recipientName(request.getRecipientName())
                .isDefault(makeDefault)
                .user(currentUser)
                .build();

        Address saved = addressRepository.save(address);
        return Response.ok(mapToResponse(saved));
    }

    @Transactional
    public Response<AddressResponse> updateAddress(String sku, UpdateAddressRequest request) {
        User currentUser = getCurrentAuthenticatedUser();

        Address address = addressRepository.findBySkuAndUserId(sku, currentUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Địa chỉ không tồn tại hoặc không thuộc quyền sở hữu của bạn."));

        if (request.getRecipientName() != null && !request.getRecipientName().isBlank()) {
            address.setRecipientName(request.getRecipientName());
        }

        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            address.setPhoneNumber(request.getPhoneNumber());
        }

        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            ValidatedGeoLocation geo = checkAndResolveAddress(request.getAddress(), request.getLatitude(), request.getLongitude());
            address.setAddress(geo.formattedAddress());
            address.setLatitude(geo.latitude());
            address.setLongitude(geo.longitude());
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            if (!addressResolutionService.isInsideVietnam(request.getLatitude(), request.getLongitude())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tọa độ nằm ngoài lãnh thổ Việt Nam.");
            }
            address.setLatitude(request.getLatitude());
            address.setLongitude(request.getLongitude());
        }

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unmarkCurrentDefaultAddress(currentUser.getId());
            address.setIsDefault(true);
        }

        Address updated = addressRepository.save(address);
        return Response.ok(mapToResponse(updated));
    }

    @Transactional(readOnly = true)
    public Response<List<AddressResponse>> getMyAddresses() {
        User currentUser = getCurrentAuthenticatedUser();
        List<Address> addresses = addressRepository.findByUserId(currentUser.getId());

        List<AddressResponse> responses = addresses.stream()
                .sorted((a1, a2) -> Boolean.compare(Boolean.TRUE.equals(a2.getIsDefault()), Boolean.TRUE.equals(a1.getIsDefault())))
                .map(this::mapToResponse)
                .toList();

        return Response.ok(responses);
    }

    @Transactional(readOnly = true)
    public Response<AddressResponse> getDefaultAddress() {
        User currentUser = getCurrentAuthenticatedUser();
        Address defaultAddress = addressRepository.findByUserIdAndIsDefaultTrue(currentUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Chưa thiết lập địa chỉ mặc định."));

        return Response.ok(mapToResponse(defaultAddress));
    }

    @Transactional
    public Response<String> setDefaultAddress(String sku) {
        User currentUser = getCurrentAuthenticatedUser();

        Address address = addressRepository.findBySkuAndUserId(sku, currentUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Địa chỉ không tồn tại."));

        unmarkCurrentDefaultAddress(currentUser.getId());
        address.setIsDefault(true);
        addressRepository.save(address);

        return Response.ok("Đã đặt làm địa chỉ mặc định thành công.");
    }

    @Transactional
    public Response<String> deleteAddress(String sku) {
        User currentUser = getCurrentAuthenticatedUser();

        Address address = addressRepository.findBySkuAndUserId(sku, currentUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Địa chỉ không tồn tại."));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressRepository.delete(address);

        // Nếu vừa xóa địa chỉ mặc định, tự động gán địa chỉ đầu tiên còn lại làm mặc định
        if (wasDefault) {
            List<Address> remaining = addressRepository.findByUserId(currentUser.getId());
            if (!remaining.isEmpty()) {
                Address newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
            }
        }

        return Response.ok("Xóa địa chỉ thành công.");
    }

    public Response<ResolvedAddress> resolvePreview(String rawAddress) {
        ResolvedAddress resolved = addressResolutionService.resolve(rawAddress);
        return Response.ok(resolved);
    }

    private void unmarkCurrentDefaultAddress(Long userId) {
        addressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(addr -> {
            addr.setIsDefault(false);
            addressRepository.save(addr);
        });
    }

    private User getCurrentAuthenticatedUser() {
        String email = securityUtil.getCurrentUsername();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Người dùng không tồn tại."));
    }

    private AddressResponse mapToResponse(Address address) {
        return AddressResponse.builder()
                .sku(address.getSku())
                .address(address.getAddress())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .phoneNumber(address.getPhoneNumber())
                .recipientName(address.getRecipientName())
                .isDefault(address.getIsDefault())
                .createdAt(address.getAuditInfo() != null ? address.getAuditInfo().getCreatedAt() : null)
                .updatedAt(address.getAuditInfo() != null ? address.getAuditInfo().getUpdatedAt() : null)
                .build();
    }
}
