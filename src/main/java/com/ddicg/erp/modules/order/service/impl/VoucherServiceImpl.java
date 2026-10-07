package com.ddicg.erp.modules.order.service.impl;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.iam.model.User;

import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.dto.request.CreateVoucherRequest;
import com.ddicg.erp.modules.order.dto.request.UpdateVoucherRequest;
import com.ddicg.erp.modules.order.dto.response.VoucherResponseDto;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import com.ddicg.erp.modules.order.service.VoucherService;
import com.ddicg.erp.modules.order.service.discount.VoucherRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherRedisService voucherRedisService;
    private final SecurityUtil securityUtil;

    @Override
    @Transactional
    public VoucherResponseDto createVoucher(CreateVoucherRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (voucherRepository.findByCode(code).isPresent()) {
            throw new BusinessException("Mã voucher '" + code + "' đã tồn tại");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        String skusStr = null;
        if (request.getApplicableSkus() != null && !request.getApplicableSkus().isEmpty()) {
            skusStr = String.join(",", request.getApplicableSkus());
        }

        Voucher voucher = Voucher.builder()
                .code(code)
                .name(request.getName().trim())
                .description(request.getDescription())
                .voucherType(request.getVoucherType())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : 0.0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .applicableSkus(skusStr)
                .totalQuantity(request.getTotalQuantity())
                .usedQuantity(0)
                .minRequiredRank(request.getMinRequiredRank() != null ? request.getMinRequiredRank() : UserRank.MEMBER)
                .isExclusive(request.getIsExclusive() != null ? request.getIsExclusive() : false)
                .isActive(true)
                .build();

        Voucher saved = voucherRepository.save(voucher);

        // Nạp ngay lên Redis với TTL = (endDate - now)
        voucherRedisService.cacheVoucher(saved);

        log.info("✅ VOUCHER_CREATED: Code: {}, Type: {}, Total: {}", saved.getCode(), saved.getVoucherType(), saved.getTotalQuantity());
        return VoucherResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public VoucherResponseDto updateVoucher(Long id, UpdateVoucherRequest request) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy voucher với id: " + id));

        if (request.getName() != null) voucher.setName(request.getName().trim());
        if (request.getDescription() != null) voucher.setDescription(request.getDescription());
        if (request.getVoucherType() != null) voucher.setVoucherType(request.getVoucherType());
        if (request.getDiscountType() != null) voucher.setDiscountType(request.getDiscountType());
        if (request.getDiscountValue() != null) voucher.setDiscountValue(request.getDiscountValue());
        if (request.getMaxDiscountAmount() != null) voucher.setMaxDiscountAmount(request.getMaxDiscountAmount());
        if (request.getMinOrderAmount() != null) voucher.setMinOrderAmount(request.getMinOrderAmount());
        if (request.getStartDate() != null) voucher.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) voucher.setEndDate(request.getEndDate());
        if (request.getTotalQuantity() != null) voucher.setTotalQuantity(request.getTotalQuantity());
        if (request.getIsActive() != null) voucher.setIsActive(request.getIsActive());
        if (request.getMinRequiredRank() != null) voucher.setMinRequiredRank(request.getMinRequiredRank());
        if (request.getIsExclusive() != null) voucher.setIsExclusive(request.getIsExclusive());

        if (request.getApplicableSkus() != null) {
            voucher.setApplicableSkus(String.join(",", request.getApplicableSkus()));
        }

        if (voucher.getEndDate().isBefore(voucher.getStartDate())) {
            throw new BusinessException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        Voucher saved = voucherRepository.save(voucher);

        // Đồng bộ cập nhật lại trên Redis
        if (saved.isCurrentlyValid()) {
            voucherRedisService.cacheVoucher(saved);
        } else {
            voucherRedisService.evictVoucher(saved.getCode());
        }

        log.info("🔄 VOUCHER_UPDATED: ID: {}, Code: {}", saved.getId(), saved.getCode());
        return VoucherResponseDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy voucher với id: " + id));

        String currentUser = getCurrentUsername();
        voucher.markDeletedNow(currentUser);
        voucher.setIsActive(false);
        voucherRepository.save(voucher);

        // Xóa ngay khỏi Redis
        voucherRedisService.evictVoucher(voucher.getCode());
        log.info("🗑️ VOUCHER_SOFT_DELETED: ID: {}, Code: {}", id, voucher.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherResponseDto getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy voucher với id: " + id));
        return VoucherResponseDto.fromEntity(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherResponseDto getVoucherByCode(String code) {
        Voucher voucher = voucherRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessException("Không tìm thấy voucher với mã: " + code));
        return VoucherResponseDto.fromEntity(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public PagingResponse<VoucherResponseDto> getAllVouchers(Pageable pageable) {
        Page<Voucher> page = voucherRepository.findAll(pageable);
        return PagingResponse.from(page.map(VoucherResponseDto::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoucherResponseDto> getActiveVouchers() {
        UserRank currentRank = securityUtil.getCurrentUser()
                .map(User::getUserRank)
                .orElse(UserRank.MEMBER);
        if (currentRank == null) {
            currentRank = UserRank.MEMBER;
        }
        final UserRank userRank = currentRank;

        return voucherRepository.findAll().stream()
                .filter(Voucher::isCurrentlyValid)
                .filter(v -> {
                    UserRank minRank = v.getMinRequiredRank() != null ? v.getMinRequiredRank() : UserRank.MEMBER;
                    return userRank.ordinal() >= minRank.ordinal();
                })
                .map(VoucherResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public VoucherResponseDto checkVoucher(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Mã voucher không được để trống");
        }

        // Đọc siêu tốc từ Redis Cache (<0.2ms)
        Optional<VoucherCacheDto> cacheOpt = voucherRedisService.getValidVoucher(code);
        if (cacheOpt.isEmpty()) {
            throw new BusinessException("Mã voucher '" + code + "' không tồn tại hoặc đã hết hạn/hết lượt sử dụng");
        }

        VoucherCacheDto cached = cacheOpt.get();

        UserRank currentRank = securityUtil.getCurrentUser()
                .map(User::getUserRank)
                .orElse(UserRank.MEMBER);
        if (currentRank == null) {
            currentRank = UserRank.MEMBER;
        }

        UserRank minRequiredRank = cached.getMinRequiredRank() != null ? cached.getMinRequiredRank() : UserRank.MEMBER;
        if (currentRank.ordinal() < minRequiredRank.ordinal()) {
            throw new BusinessException("Bạn không đủ điều kiện sử dụng mã voucher này (Yêu cầu hạng hội viên tối thiểu: " + minRequiredRank.name() + ")");
        }

        int total = cached.getTotalQuantity() != null ? cached.getTotalQuantity() : 0;
        int used = cached.getUsedQuantity() != null ? cached.getUsedQuantity() : 0;
        int available = Math.max(0, total - used);

        return VoucherResponseDto.builder()
                .id(cached.getId())
                .code(cached.getCode())
                .name(cached.getName())
                .voucherType(cached.getVoucherType())
                .discountType(cached.getDiscountType())
                .discountValue(cached.getDiscountValue())
                .maxDiscountAmount(cached.getMaxDiscountAmount())
                .minOrderAmount(cached.getMinOrderAmount())
                .applicableSkus(cached.getApplicableSkus())
                .minRequiredRank(cached.getMinRequiredRank())
                .isExclusive(cached.getIsExclusive())
                .startDate(cached.getStartDate())
                .endDate(cached.getEndDate())
                .totalQuantity(total)
                .usedQuantity(used)
                .availableQuantity(available)
                .isValid(true)
                .build();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "SYSTEM";
    }
}
