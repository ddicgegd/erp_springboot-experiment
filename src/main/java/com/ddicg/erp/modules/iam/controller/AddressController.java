package com.ddicg.erp.modules.iam.controller;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.iam.dto.request.CreateAddressRequest;
import com.ddicg.erp.modules.iam.dto.request.UpdateAddressRequest;
import com.ddicg.erp.modules.iam.dto.response.AddressResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/addresses")
public interface AddressController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    Response<AddressResponse> createAddress(@Valid @RequestBody CreateAddressRequest request);

    @PutMapping("/{sku}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<AddressResponse> updateAddress(
            @PathVariable String sku,
            @Valid @RequestBody UpdateAddressRequest request);

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<List<AddressResponse>> getMyAddresses();

    @GetMapping("/me/default")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<AddressResponse> getDefaultAddress();

    @PatchMapping("/{sku}/default")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<String> setDefaultAddress(@PathVariable String sku);

    @DeleteMapping("/{sku}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("isAuthenticated()")
    Response<String> deleteAddress(@PathVariable String sku);

    @GetMapping("/resolve")
    @ResponseStatus(HttpStatus.OK)
    Response<ResolvedAddress> previewResolve(@RequestParam("address") String address);
}
