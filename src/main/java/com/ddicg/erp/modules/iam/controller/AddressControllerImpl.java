package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.iam.dto.request.CreateAddressRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateAddressRequest;
import com.ddicg.erp.modules.iam.dto.response.AddressResponse;
import com.ddicg.erp.modules.iam.service.AddressService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressControllerImpl implements AddressController {

    AddressService addressService;

    @Override
    public Response<AddressResponse> createAddress(CreateAddressRequest request) {
        return addressService.createAddress(request);
    }

    @Override
    public Response<AddressResponse> updateAddress(String sku, UpdateAddressRequest request) {
        return addressService.updateAddress(sku, request);
    }

    @Override
    public Response<List<AddressResponse>> getMyAddresses() {
        return addressService.getMyAddresses();
    }

    @Override
    public Response<AddressResponse> getDefaultAddress() {
        return addressService.getDefaultAddress();
    }

    @Override
    public Response<String> setDefaultAddress(String sku) {
        return addressService.setDefaultAddress(sku);
    }

    @Override
    public Response<String> deleteAddress(String sku) {
        return addressService.deleteAddress(sku);
    }

    @Override
    public Response<ResolvedAddress> previewResolve(String address) {
        return addressService.resolvePreview(address);
    }
}
