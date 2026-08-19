package com.ddicg.erp.modules.order.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.dto.response.ShippingCalculationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/shipping")
public interface ShippingController {

    @GetMapping("/calculate")
    @ResponseStatus(HttpStatus.OK)
    Response<ShippingCalculationResult> calculateByAddressSku(
            @RequestParam("addressSku") String addressSku
    );

    @GetMapping("/estimate")
    @ResponseStatus(HttpStatus.OK)
    Response<ShippingCalculationResult> estimateByRawAddress(
            @RequestParam("address") String address,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude
    );
}
