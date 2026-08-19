package com.ddicg.erp.modules.order.controller;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.dto.response.ShippingCalculationResult;
import com.ddicg.erp.core.common.service.AddressResolutionService;
import com.ddicg.erp.core.common.service.ShippingCalculationService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.modules.iam.model.Address;
import com.ddicg.erp.modules.iam.repository.AddressRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShippingControllerImpl implements ShippingController {

    ShippingCalculationService shippingCalculationService;
    AddressRepository addressRepository;
    AddressResolutionService addressResolutionService;

    @Override
    public Response<ShippingCalculationResult> calculateByAddressSku(String addressSku) {
        if (addressSku == null || addressSku.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Mã SKU địa chỉ không được để trống");
        }

        Address address = addressRepository.findBySku(addressSku)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "Địa chỉ không tồn tại"));

        Double lat = address.getLatitude();
        Double lon = address.getLongitude();

        if (lat == null || lon == null) {
            ResolvedAddress resolved = addressResolutionService.resolve(address.getAddress());
            lat = resolved.getLatitude();
            lon = resolved.getLongitude();
        }

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address.getAddress());
        return Response.ok(result);
    }

    @Override
    public Response<ShippingCalculationResult> estimateByRawAddress(String rawAddress, Double latitude, Double longitude) {
        if (rawAddress == null || rawAddress.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Địa chỉ không được để trống");
        }

        Double lat = latitude;
        Double lon = longitude;

        if (lat == null || lon == null) {
            ResolvedAddress resolved = addressResolutionService.resolve(rawAddress);
            lat = resolved.getLatitude();
            lon = resolved.getLongitude();
        }

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, rawAddress);
        return Response.ok(result);
    }
}
