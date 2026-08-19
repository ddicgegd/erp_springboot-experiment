package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.modules.order.dto.request.CreateVoucherRequest;
import com.ddicg.erp.modules.order.dto.request.UpdateVoucherRequest;
import com.ddicg.erp.modules.order.dto.response.VoucherResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface VoucherService {

    VoucherResponseDto createVoucher(CreateVoucherRequest request);

    VoucherResponseDto updateVoucher(Long id, UpdateVoucherRequest request);

    void deleteVoucher(Long id);

    VoucherResponseDto getVoucherById(Long id);

    VoucherResponseDto getVoucherByCode(String code);

    PagingResponse<VoucherResponseDto> getAllVouchers(Pageable pageable);

    List<VoucherResponseDto> getActiveVouchers();

    VoucherResponseDto checkVoucher(String code);
}
