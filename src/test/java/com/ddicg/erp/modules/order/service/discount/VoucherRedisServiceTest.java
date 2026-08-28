package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherRedisServiceTest {

    @Mock
    private VoucherRepository voucherRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock mockLock;

    @InjectMocks
    private VoucherRedisService voucherRedisService;

    private Voucher sampleVoucher;

    @BeforeEach
    void setUp() {
        sampleVoucher = Voucher.builder()
                .code("DISCOUNT20")
                .name("Giảm 20k")
                .discountValue(20000.0)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .totalQuantity(100)
                .usedQuantity(0)
                .isActive(true)
                .build();
        sampleVoucher.setId(1L);
    }

    @Test
    @DisplayName("cacheVoucher -> Nạp cache với TTL Jitter chống Avalanche")
    void testCacheVoucher_WithJitter() {
        voucherRedisService.cacheVoucher(sampleVoucher);

        verify(redisService).setValueWithJitter(
                eq(RedisTable.VOUCHER_INFO),
                eq("DISCOUNT20"),
                any(VoucherCacheDto.class),
                anyLong(),
                eq(180L),
                eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("getValidVoucher -> Hit Null Cache (Chống Penetration) -> Trả về empty không query DB")
    void testGetValidVoucher_NullCacheHit() {
        when(redisService.isNullSentinel(RedisTable.VOUCHER_INFO, "INVALID_CODE")).thenReturn(true);

        Optional<VoucherCacheDto> result = voucherRedisService.getValidVoucher("INVALID_CODE");

        assertThat(result).isEmpty();
        verifyNoInteractions(voucherRepository);
        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("getValidVoucher -> Cache Hit -> Trả về DTO trực tiếp từ Redis")
    void testGetValidVoucher_CacheHit() {
        VoucherCacheDto cachedDto = VoucherCacheDto.fromEntity(sampleVoucher);
        when(redisService.isNullSentinel(RedisTable.VOUCHER_INFO, "DISCOUNT20")).thenReturn(false);
        when(redisService.getValue(RedisTable.VOUCHER_INFO, "DISCOUNT20")).thenReturn(cachedDto);

        Optional<VoucherCacheDto> result = voucherRedisService.getValidVoucher("DISCOUNT20");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("DISCOUNT20");
        verifyNoInteractions(voucherRepository);
    }

    @Test
    @DisplayName("getValidVoucher -> Cache Miss & DB Not Found -> Giành Lock và Ghi Null Sentinel chống Penetration")
    void testGetValidVoucher_CacheMiss_DbNotFound_SetsNullSentinel() throws Exception {
        when(redisService.isNullSentinel(RedisTable.VOUCHER_INFO, "NOTFOUND")).thenReturn(false);
        when(redisService.getValue(RedisTable.VOUCHER_INFO, "NOTFOUND")).thenReturn(null);
        when(redissonClient.getLock("lock:voucher:apply:NOTFOUND")).thenReturn(mockLock);
        when(mockLock.tryLock(2, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherRepository.findByCode("NOTFOUND")).thenReturn(Optional.empty());

        Optional<VoucherCacheDto> result = voucherRedisService.getValidVoucher("NOTFOUND");

        assertThat(result).isEmpty();
        verify(redisService).setNullSentinel(RedisTable.VOUCHER_INFO, "NOTFOUND", 60);
        verify(mockLock).unlock();
    }

    @Test
    @DisplayName("getValidVoucher -> Cache Miss & DB Found -> Giành Lock và Cache kèm Jitter")
    void testGetValidVoucher_CacheMiss_DbFound_CachesResult() throws Exception {
        when(redisService.isNullSentinel(RedisTable.VOUCHER_INFO, "DISCOUNT20")).thenReturn(false);
        when(redisService.getValue(RedisTable.VOUCHER_INFO, "DISCOUNT20")).thenReturn(null);
        when(redissonClient.getLock("lock:voucher:apply:DISCOUNT20")).thenReturn(mockLock);
        when(mockLock.tryLock(2, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        when(voucherRepository.findByCode("DISCOUNT20")).thenReturn(Optional.of(sampleVoucher));

        Optional<VoucherCacheDto> result = voucherRedisService.getValidVoucher("DISCOUNT20");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("DISCOUNT20");
        verify(redisService).setValueWithJitter(
                eq(RedisTable.VOUCHER_INFO),
                eq("DISCOUNT20"),
                any(VoucherCacheDto.class),
                anyLong(),
                eq(180L),
                eq(TimeUnit.SECONDS)
        );
        verify(mockLock).unlock();
    }

    @Test
    @DisplayName("evictVoucher -> Gọi unlink xóa bất đồng bộ")
    void testEvictVoucher() {
        voucherRedisService.evictVoucher("DISCOUNT20");

        verify(redisService).unlink("voucher:info:DISCOUNT20");
    }
}
