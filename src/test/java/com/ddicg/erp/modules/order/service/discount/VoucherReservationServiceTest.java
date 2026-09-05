package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucherReservationServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private VoucherReservationService voucherReservationService;

    private Voucher validVoucher;
    private RLock mockLock;

    @SuppressWarnings("rawtypes")
    private RBucket mockBucket;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() throws InterruptedException {
        validVoucher = Voucher.builder()
                .code("SALE10")
                .name("Giảm 10%")
                .usedQuantity(0)
                .totalQuantity(100)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .minOrderAmount(0.0)
                .isActive(true)
                .build();

        mockLock = mock(RLock.class);
        mockBucket = mock(RBucket.class);

        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doReturn(mockBucket).when(redissonClient).getBucket(anyString());
    }

    @Test
    @DisplayName("reserveVouchers: Danh sách rỗng -> Không làm gì cả")
    void reserveVouchers_emptyList_shouldDoNothing() {
        voucherReservationService.reserveVouchers(Collections.emptyList(), "ORD-001", 500000.0);

        verifyNoInteractions(redissonClient);
        verifyNoInteractions(voucherRepository);
    }

    @Test
    @DisplayName("reserveVouchers: Voucher hợp lệ -> Acquire lock, set Redis hold với TTL 180s")
    @SuppressWarnings("unchecked")
    void reserveVouchers_validVoucher_shouldAcquireLockAndSetRedisHold() {
        when(voucherRepository.findByCode("SALE10")).thenReturn(Optional.of(validVoucher));

        voucherReservationService.reserveVouchers(List.of("SALE10"), "ORD-HOLD-001", 500000.0);

        verify(redissonClient).getLock("voucher:lock:SALE10");
        verify(redissonClient).getBucket("voucher:hold:ORD-HOLD-001:SALE10");
        verify(mockBucket).set(eq("RESERVED"), any(java.time.Duration.class));
        verify(mockLock).unlock();
    }

    @Test
    @DisplayName("reserveVouchers: Voucher không tồn tại -> Ném BusinessException")
    void reserveVouchers_voucherNotFound_shouldThrowBusinessException() {
        when(voucherRepository.findByCode("NONEXIST")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                voucherReservationService.reserveVouchers(List.of("NONEXIST"), "ORD-X", 500000.0));

        assertTrue(ex.getMessage().contains("NONEXIST"));
    }

    @Test
    @DisplayName("reserveVouchers: Đơn hàng chưa đạt giá trị tối thiểu -> Ném BusinessException")
    void reserveVouchers_orderBelowMinAmount_shouldThrowBusinessException() {
        Voucher highMinVoucher = Voucher.builder()
                .code("BIG10")
                .name("Giảm đơn lớn")
                .usedQuantity(0)
                .totalQuantity(100)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .minOrderAmount(1000000.0)
                .isActive(true)
                .build();

        when(voucherRepository.findByCode("BIG10")).thenReturn(Optional.of(highMinVoucher));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                voucherReservationService.reserveVouchers(List.of("BIG10"), "ORD-SMALL", 500000.0));

        assertTrue(ex.getMessage().contains("giá trị tối thiểu"));
    }

    @Test
    @DisplayName("commitVouchers: Danh sách rỗng -> Không làm gì cả")
    void commitVouchers_emptyList_shouldDoNothing() {
        voucherReservationService.commitVouchers(Collections.emptyList(), "ORD-001");

        verifyNoInteractions(voucherRepository);
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("commitVouchers: Voucher hợp lệ -> Tăng usedQuantity và xóa Redis hold")
    @SuppressWarnings("unchecked")
    void commitVouchers_validVoucher_shouldIncrementUsedQuantityAndDeleteHold() {
        when(voucherRepository.findByCode("SALE10")).thenReturn(Optional.of(validVoucher));

        voucherReservationService.commitVouchers(List.of("SALE10"), "ORD-COMMIT-001");

        assertEquals(1, validVoucher.getUsedQuantity());
        verify(voucherRepository).save(validVoucher);
        verify(mockBucket).delete();
    }

    @Test
    @DisplayName("releaseVouchers: Danh sách rỗng -> Không làm gì cả")
    void releaseVouchers_emptyList_shouldDoNothing() {
        voucherReservationService.releaseVouchers(Collections.emptyList(), "ORD-001");

        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("releaseVouchers: Voucher hợp lệ -> Xóa key hold trên Redis")
    void releaseVouchers_validVoucher_shouldDeleteRedisHoldKey() {
        voucherReservationService.releaseVouchers(List.of("FREESHIP50"), "ORD-RELEASE-001");

        verify(redissonClient).getBucket("voucher:hold:ORD-RELEASE-001:FREESHIP50");
        verify(mockBucket).delete();
    }
}
