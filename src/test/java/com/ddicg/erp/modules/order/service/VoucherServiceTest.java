package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.model.User;
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
import java.util.List;

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
    @Mock
    private SecurityUtil securityUtil;

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
    @DisplayName("Kiểm tra voucher thất bại nếu rank của user thấp hơn minRequiredRank")
    void testCheckVoucher_InsufficientRank_ThrowsException() {
        VoucherCacheDto cacheDto = VoucherCacheDto.builder()
                .code("VIPGOLD")
                .voucherType(VoucherType.GLOBAL_ORDER)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(20.0)
                .minRequiredRank(UserRank.GOLD)
                .totalQuantity(100)
                .usedQuantity(0)
                .isActive(true)
                .build();

        User normalUser = User.builder()
                .name("user1")
                .rank(UserRank.MEMBER)
                .build();

        when(voucherRedisService.getValidVoucher("VIPGOLD")).thenReturn(Optional.of(cacheDto));
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(normalUser));

        assertThatThrownBy(() -> voucherService.checkVoucher("VIPGOLD"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không đủ điều kiện");
    }

    @Test
    @DisplayName("Kiểm tra voucher thành công nếu rank của user thỏa mãn minRequiredRank")
    void testCheckVoucher_SufficientRank_Success() {
        VoucherCacheDto cacheDto = VoucherCacheDto.builder()
                .code("VIPSILVER")
                .voucherType(VoucherType.GLOBAL_ORDER)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(15.0)
                .minRequiredRank(UserRank.SILVER)
                .totalQuantity(100)
                .usedQuantity(0)
                .isActive(true)
                .build();

        User goldUser = User.builder()
                .name("goldUser")
                .rank(UserRank.GOLD)
                .build();

        when(voucherRedisService.getValidVoucher("VIPSILVER")).thenReturn(Optional.of(cacheDto));
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(goldUser));

        VoucherResponseDto response = voucherService.checkVoucher("VIPSILVER");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("VIPSILVER");
        assertThat(response.getMinRequiredRank()).isEqualTo(UserRank.SILVER);
    }

    @Test
    @DisplayName("Lấy active vouchers chỉ trả về voucher có minRequiredRank <= userRank")
    void testGetActiveVouchers_RankFiltering() {
        Voucher memberVoucher = Voucher.builder()
                .id(1L)
                .code("MEMBER_VOUCHER")
                .minRequiredRank(UserRank.MEMBER)
                .totalQuantity(10)
                .usedQuantity(0)
                .isActive(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(10))
                .build();

        Voucher goldVoucher = Voucher.builder()
                .id(2L)
                .code("GOLD_VOUCHER")
                .minRequiredRank(UserRank.GOLD)
                .totalQuantity(10)
                .usedQuantity(0)
                .isActive(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(10))
                .build();

        when(voucherRepository.findAll()).thenReturn(java.util.List.of(memberVoucher, goldVoucher));

        // 1. Unauthenticated or MEMBER user -> Only MEMBER_VOUCHER
        when(securityUtil.getCurrentUser()).thenReturn(Optional.empty());
        var vouchersForMember = voucherService.getActiveVouchers();
        assertThat(vouchersForMember).hasSize(1);
        assertThat(vouchersForMember.get(0).getCode()).isEqualTo("MEMBER_VOUCHER");

        // 2. DIAMOND user -> both MEMBER_VOUCHER and GOLD_VOUCHER
        User diamondUser = User.builder().rank(UserRank.DIAMOND).build();
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(diamondUser));
        var vouchersForDiamond = voucherService.getActiveVouchers();
        assertThat(vouchersForDiamond).hasSize(2);
    }

    @Test
    @DisplayName("checkVoucher: Khách vãng lai hoặc MEMBER không áp dụng được SILVER/GOLD/DIAMOND")
    void testCheckVoucher_UnauthenticatedOrMember_ThrowsException_ForSilverGoldDiamond() {
        LocalDateTime now = LocalDateTime.now();
        VoucherCacheDto silverDto = VoucherCacheDto.builder()
                .code("SILVER_V")
                .minRequiredRank(UserRank.SILVER)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(5))
                .build();
        VoucherCacheDto diamondDto = VoucherCacheDto.builder()
                .code("DIAMOND_V")
                .minRequiredRank(UserRank.DIAMOND)
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(5))
                .build();

        when(voucherRedisService.getValidVoucher("SILVER_V")).thenReturn(Optional.of(silverDto));
        when(voucherRedisService.getValidVoucher("DIAMOND_V")).thenReturn(Optional.of(diamondDto));

        // 1. Unauthenticated / guest -> Không dùng được SILVER_V
        when(securityUtil.getCurrentUser()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> voucherService.checkVoucher("SILVER_V"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không đủ điều kiện");

        // 2. MEMBER user -> Không dùng được DIAMOND_V
        User memberUser = User.builder().rank(UserRank.MEMBER).build();
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(memberUser));
        assertThatThrownBy(() -> voucherService.checkVoucher("DIAMOND_V"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không đủ điều kiện");
    }

    @Test
    @DisplayName("checkVoucher: User BRONZE không áp dụng được voucher GOLD")
    void testCheckVoucher_BronzeUser_CannotUseGoldVoucher() {
        VoucherCacheDto goldDto = VoucherCacheDto.builder()
                .code("GOLD_SPECIAL")
                .minRequiredRank(UserRank.GOLD)
                .build();
        User bronzeUser = User.builder().rank(UserRank.BRONZE).build();

        when(voucherRedisService.getValidVoucher("GOLD_SPECIAL")).thenReturn(Optional.of(goldDto));
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(bronzeUser));

        assertThatThrownBy(() -> voucherService.checkVoucher("GOLD_SPECIAL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không đủ điều kiện sử dụng mã voucher này (Yêu cầu hạng hội viên tối thiểu: GOLD)");
    }

    @Test
    @DisplayName("checkVoucher: User GOLD áp dụng voucher SILVER thành công và trả về đầy đủ metadata")
    void testCheckVoucher_GoldUser_AppliesSilver_ReturnsFullMetadata() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        VoucherCacheDto silverDto = VoucherCacheDto.builder()
                .id(99L)
                .code("SILVER_DEAL")
                .name("Ưu đãi Silver")
                .voucherType(VoucherType.GLOBAL_ORDER)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(15.0)
                .maxDiscountAmount(50000.0)
                .minOrderAmount(200000.0)
                .minRequiredRank(UserRank.SILVER)
                .isExclusive(true)
                .totalQuantity(100)
                .usedQuantity(25)
                .startDate(start)
                .endDate(end)
                .build();
        User goldUser = User.builder().rank(UserRank.GOLD).build();

        when(voucherRedisService.getValidVoucher("SILVER_DEAL")).thenReturn(Optional.of(silverDto));
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(goldUser));

        VoucherResponseDto res = voucherService.checkVoucher("SILVER_DEAL");

        assertThat(res).isNotNull();
        assertThat(res.getCode()).isEqualTo("SILVER_DEAL");
        assertThat(res.getMinRequiredRank()).isEqualTo(UserRank.SILVER);
        assertThat(res.getIsExclusive()).isTrue();
        assertThat(res.getTotalQuantity()).isEqualTo(100);
        assertThat(res.getUsedQuantity()).isEqualTo(25);
        assertThat(res.getAvailableQuantity()).isEqualTo(75);
        assertThat(res.getStartDate()).isEqualTo(start);
        assertThat(res.getEndDate()).isEqualTo(end);
        assertThat(res.getIsValid()).isTrue();
    }

    @Test
    @DisplayName("checkVoucher: User DIAMOND áp dụng voucher DIAMOND thành công")
    void testCheckVoucher_DiamondUser_AppliesDiamond_Success() {
        VoucherCacheDto diamondDto = VoucherCacheDto.builder()
                .code("DIAMOND_EXCLUSIVE")
                .minRequiredRank(UserRank.DIAMOND)
                .totalQuantity(10)
                .usedQuantity(2)
                .build();
        User diamondUser = User.builder().rank(UserRank.DIAMOND).build();

        when(voucherRedisService.getValidVoucher("DIAMOND_EXCLUSIVE")).thenReturn(Optional.of(diamondDto));
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(diamondUser));

        VoucherResponseDto res = voucherService.checkVoucher("DIAMOND_EXCLUSIVE");
        assertThat(res).isNotNull();
        assertThat(res.getCode()).isEqualTo("DIAMOND_EXCLUSIVE");
        assertThat(res.getIsValid()).isTrue();
    }

    @Test
    @DisplayName("getActiveVouchers: User GOLD thấy voucher MEMBER, BRONZE, SILVER, GOLD nhưng loại trừ PLATINUM, DIAMOND")
    void testGetActiveVouchers_GoldUser_SeesUpToGold_ExcludesPlatinumDiamond() {
        LocalDateTime now = LocalDateTime.now();
        Voucher vMember = Voucher.builder().id(1L).code("V_MEMBER").minRequiredRank(UserRank.MEMBER).startDate(now.minusDays(1)).endDate(now.plusDays(2)).totalQuantity(10).usedQuantity(0).isActive(true).build();
        Voucher vBronze = Voucher.builder().id(2L).code("V_BRONZE").minRequiredRank(UserRank.BRONZE).startDate(now.minusDays(1)).endDate(now.plusDays(2)).totalQuantity(10).usedQuantity(0).isActive(true).build();
        Voucher vSilver = Voucher.builder().id(3L).code("V_SILVER").minRequiredRank(UserRank.SILVER).startDate(now.minusDays(1)).endDate(now.plusDays(2)).totalQuantity(10).usedQuantity(0).isActive(true).build();
        Voucher vGold = Voucher.builder().id(4L).code("V_GOLD").minRequiredRank(UserRank.GOLD).startDate(now.minusDays(1)).endDate(now.plusDays(2)).totalQuantity(10).usedQuantity(0).isActive(true).build();
        Voucher vPlat = Voucher.builder().id(5L).code("V_PLATINUM").minRequiredRank(UserRank.PLATINUM).startDate(now.minusDays(1)).endDate(now.plusDays(2)).totalQuantity(10).usedQuantity(0).isActive(true).build();
        Voucher vDia = Voucher.builder().id(6L).code("V_DIAMOND").minRequiredRank(UserRank.DIAMOND).startDate(now.minusDays(1)).endDate(now.plusDays(2)).totalQuantity(10).usedQuantity(0).isActive(true).build();

        when(voucherRepository.findAll()).thenReturn(List.of(vMember, vBronze, vSilver, vGold, vPlat, vDia));

        User goldUser = User.builder().rank(UserRank.GOLD).build();
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(goldUser));

        List<VoucherResponseDto> results = voucherService.getActiveVouchers();
        List<String> codes = results.stream().map(VoucherResponseDto::getCode).toList();

        assertThat(codes).containsExactlyInAnyOrder("V_MEMBER", "V_BRONZE", "V_SILVER", "V_GOLD");
        assertThat(codes).doesNotContain("V_PLATINUM", "V_DIAMOND");
    }

    @Test
    @DisplayName("updateVoucher hợp lệ -> Cập nhật DB và re-cache vào Redis")
    void testUpdateVoucher_Valid_ReCachesInRedis() {
        LocalDateTime now = LocalDateTime.now();
        Voucher existing = Voucher.builder()
                .id(50L)
                .code("UP_TEST")
                .name("Old Name")
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(10))
                .totalQuantity(50)
                .usedQuantity(5)
                .isActive(true)
                .build();

        UpdateVoucherRequest req = UpdateVoucherRequest.builder()
                .name("New Name")
                .minRequiredRank(UserRank.SILVER)
                .isExclusive(true)
                .build();

        when(voucherRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));

        VoucherResponseDto updated = voucherService.updateVoucher(50L, req);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getMinRequiredRank()).isEqualTo(UserRank.SILVER);
        verify(voucherRedisService, times(1)).cacheVoucher(any(Voucher.class));
        verify(voucherRedisService, never()).evictVoucher(anyString());
    }

    @Test
    @DisplayName("updateVoucher không còn hợp lệ (isActive=false) -> Evict khỏi Redis")
    void testUpdateVoucher_Inactive_EvictsFromRedis() {
        LocalDateTime now = LocalDateTime.now();
        Voucher existing = Voucher.builder()
                .id(51L)
                .code("UP_INACTIVE")
                .name("Deactivate Test")
                .startDate(now.minusDays(1))
                .endDate(now.plusDays(10))
                .totalQuantity(50)
                .usedQuantity(5)
                .isActive(true)
                .build();

        UpdateVoucherRequest req = UpdateVoucherRequest.builder()
                .isActive(false)
                .build();

        when(voucherRepository.findById(51L)).thenReturn(Optional.of(existing));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));

        voucherService.updateVoucher(51L, req);

        verify(voucherRedisService, times(1)).evictVoucher("UP_INACTIVE");
        verify(voucherRedisService, never()).cacheVoucher(any(Voucher.class));
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
