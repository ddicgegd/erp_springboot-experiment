package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.dto.request.CreateVoucherRequest;
import com.ddicg.erp.modules.order.dto.request.UpdateVoucherRequest;
import com.ddicg.erp.modules.order.dto.response.VoucherResponseDto;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import com.ddicg.erp.modules.order.service.discount.VoucherRedisService;
import com.ddicg.erp.modules.order.service.impl.VoucherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private VoucherRedisService voucherRedisService;

    @InjectMocks
    private VoucherServiceImpl voucherService;

    private Voucher sampleVoucher;

    @BeforeEach
    void setUp() {
        sampleVoucher = Voucher.builder()
                .code("FREESHIP")
                .name("Miễn phí vận chuyển")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(30000.0)
                .minOrderAmount(100000.0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(10))
                .totalQuantity(100)
                .usedQuantity(0)
                .isActive(true)
                .build();
        sampleVoucher.setId(1L);
    }

    @Test
    @DisplayName("Tạo voucher thành công -> Lưu DB và nạp Cache Redis")
    void testCreateVoucher_Success() {
        CreateVoucherRequest request = CreateVoucherRequest.builder()
                .code("FREESHIP")
                .name("Miễn phí vận chuyển")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(30000.0)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .totalQuantity(50)
                .build();

        when(voucherRepository.findByCode("FREESHIP")).thenReturn(Optional.empty());
        when(voucherRepository.save(any(Voucher.class))).thenReturn(sampleVoucher);

        VoucherResponseDto response = voucherService.createVoucher(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("FREESHIP");
        verify(voucherRedisService, times(1)).cacheVoucher(any(Voucher.class));
    }

    @Test
    @DisplayName("Tạo voucher thất bại nếu mã đã tồn tại")
    void testCreateVoucher_DuplicateCode_ThrowsException() {
        CreateVoucherRequest request = CreateVoucherRequest.builder()
                .code("FREESHIP")
                .name("Miễn phí vận chuyển")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(30000.0)
                .startDate(LocalDateTime.now().plusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .totalQuantity(50)
                .build();

        when(voucherRepository.findByCode("FREESHIP")).thenReturn(Optional.of(sampleVoucher));

        assertThatThrownBy(() -> voucherService.createVoucher(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    @DisplayName("Kiểm tra mã voucher hợp lệ từ Redis Cache")
    void testCheckVoucher_Success() {
        VoucherCacheDto cacheDto = VoucherCacheDto.builder()
                .code("FREESHIP")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(30000.0)
                .totalQuantity(100)
                .usedQuantity(10)
                .isActive(true)
                .build();

        when(voucherRedisService.getValidVoucher("FREESHIP")).thenReturn(Optional.of(cacheDto));

        VoucherResponseDto response = voucherService.checkVoucher("FREESHIP");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("FREESHIP");
        assertThat(response.getIsValid()).isTrue();
    }

    @Test
    @DisplayName("Xóa mềm voucher -> Evict khỏi Redis")
    void testDeleteVoucher_Success() {
        when(voucherRepository.findById(1L)).thenReturn(Optional.of(sampleVoucher));
        when(voucherRepository.save(any(Voucher.class))).thenReturn(sampleVoucher);

        voucherService.deleteVoucher(1L);

        verify(voucherRepository, times(1)).save(sampleVoucher);
        verify(voucherRedisService, times(1)).evictVoucher("FREESHIP");
    }
}
