package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.iam.dto.request.CreateAddressRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateAddressRequest;
import com.ddicg.erp.modules.iam.dto.response.AddressResponse;
import com.ddicg.erp.modules.iam.service.AddressService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressController {

    AddressService addressService;

    @PostMapping
    public Response<AddressResponse> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        return addressService.createAddress(request);
    }

    @PutMapping("/{sku}")
    public Response<AddressResponse> updateAddress(
            @PathVariable String sku,
            @Valid @RequestBody UpdateAddressRequest request) {
        return addressService.updateAddress(sku, request);
    }

    @GetMapping("/me")
    public Response<List<AddressResponse>> getMyAddresses() {
        return addressService.getMyAddresses();
    }

    @GetMapping("/me/default")
    public Response<AddressResponse> getDefaultAddress() {
        return addressService.getDefaultAddress();
    }

    @PatchMapping("/{sku}/default")
    public Response<String> setDefaultAddress(@PathVariable String sku) {
        return addressService.setDefaultAddress(sku);
    }

    @DeleteMapping("/{sku}")
    public Response<String> deleteAddress(@PathVariable String sku) {
        return addressService.deleteAddress(sku);
    }

    @GetMapping("/resolve")
    public Response<ResolvedAddress> previewResolve(@RequestParam("address") String address) {
        return addressService.resolvePreview(address);
    }
}
