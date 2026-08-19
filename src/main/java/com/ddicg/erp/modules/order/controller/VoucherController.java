package com.ddicg.erp.modules.order.controller;

import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.order.dto.request.CreateVoucherRequest;
import com.ddicg.erp.modules.order.dto.request.UpdateVoucherRequest;
import com.ddicg.erp.modules.order.dto.response.VoucherResponseDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/vouchers")
public interface VoucherController {

    /* ==================== Customer Voucher Operations ==================== */

    /**
     * Khách hàng xem danh sách voucher đang có hiệu lực và còn lượt dùng.
     */
    @GetMapping("/active")
    @ResponseStatus(HttpStatus.OK)
    Response<List<VoucherResponseDto>> getActiveVouchers();

    /**
     * Khách hàng kiểm tra thử 1 mã voucher (đọc siêu tốc từ Redis Cache).
     */
    @GetMapping("/check/{code}")
    @ResponseStatus(HttpStatus.OK)
    Response<VoucherResponseDto> checkVoucher(@PathVariable String code);

    /* ==================== Admin Voucher CRUD Operations ==================== */

    /**
     * Quản trị viên tạo mới voucher.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response<VoucherResponseDto> createVoucher(@Valid @RequestBody CreateVoucherRequest request);

    /**
     * Quản trị viên lấy danh sách toàn bộ voucher (có phân trang).
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    Response<PagingResponse<VoucherResponseDto>> getAllVouchers(@PageableDefault(size = 20) Pageable pageable);

    /**
     * Quản trị viên xem chi tiết 1 voucher theo ID.
     */
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    Response<VoucherResponseDto> getVoucherById(@PathVariable Long id);

    /**
     * Quản trị viên cập nhật thông tin voucher.
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    Response<VoucherResponseDto> updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody UpdateVoucherRequest request
    );

    /**
     * Quản trị viên xóa mềm voucher.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    Response<Void> deleteVoucher(@PathVariable Long id);
}
