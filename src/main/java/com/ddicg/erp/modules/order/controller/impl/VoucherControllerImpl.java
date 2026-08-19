package com.ddicg.erp.modules.order.controller.impl;

import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.order.controller.VoucherController;
import com.ddicg.erp.modules.order.dto.request.CreateVoucherRequest;
import com.ddicg.erp.modules.order.dto.request.UpdateVoucherRequest;
import com.ddicg.erp.modules.order.dto.response.VoucherResponseDto;
import com.ddicg.erp.modules.order.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VoucherControllerImpl implements VoucherController {

    private final VoucherService voucherService;

    @Override
    public Response<List<VoucherResponseDto>> getActiveVouchers() {
        return Response.ok(voucherService.getActiveVouchers(), "Lấy danh sách voucher khả dụng thành công");
    }

    @Override
    public Response<VoucherResponseDto> checkVoucher(String code) {
        return Response.ok(voucherService.checkVoucher(code), "Mã voucher hợp lệ");
    }

    @Override
    public Response<VoucherResponseDto> createVoucher(CreateVoucherRequest request) {
        return Response.created(voucherService.createVoucher(request));
    }

    @Override
    public Response<PagingResponse<VoucherResponseDto>> getAllVouchers(Pageable pageable) {
        return Response.ok(voucherService.getAllVouchers(pageable), "Lấy danh sách voucher thành công");
    }

    @Override
    public Response<VoucherResponseDto> getVoucherById(Long id) {
        return Response.ok(voucherService.getVoucherById(id), "Lấy thông tin voucher thành công");
    }

    @Override
    public Response<VoucherResponseDto> updateVoucher(Long id, UpdateVoucherRequest request) {
        return Response.ok(voucherService.updateVoucher(id, request), "Cập nhật voucher thành công");
    }

    @Override
    public Response<Void> deleteVoucher(Long id) {
        voucherService.deleteVoucher(id);
        return Response.ok("Xóa voucher thành công");
    }
}
