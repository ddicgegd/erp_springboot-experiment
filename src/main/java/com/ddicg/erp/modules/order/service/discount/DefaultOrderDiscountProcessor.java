package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnMissingBean(name = "customOrderDiscountProcessor")
public class DefaultOrderDiscountProcessor implements OrderDiscountProcessor {

    private final VoucherRedisService voucherRedisService;

    @Override
    public DiscountEvaluationResult evaluateDiscount(OrderDiscountContext context) {
        if (context == null) {
            return DiscountEvaluationResult.builder().build();
        }

        List<String> rawCodes = context.getDiscountCodes() != null ? context.getDiscountCodes() : Collections.emptyList();
        List<Attributes> attributesList = context.getAttributesList() != null ? context.getAttributesList() : Collections.emptyList();
        List<CreateOrderRequest.OrderItemRequest> items = context.getItems() != null ? context.getItems() : Collections.emptyList();
        Double rawShippingFee = context.getRawShippingFee() != null ? context.getRawShippingFee() : 0.0;
        ShippingMethod shippingMethod = context.getShippingMethod();

        Map<String, Attributes> attrMap = new HashMap<>();
        for (Attributes attr : attributesList) {
            if (attr.getSku() != null && attr.getSku().getSku() != null) {
                attrMap.put(attr.getSku().getSku(), attr);
            }
        }

        // Lấy danh sách Voucher hợp lệ trực tiếp từ Redis TTL
        Map<String, com.ddicg.erp.modules.order.dto.VoucherCacheDto> voucherMap = voucherRedisService.getValidVouchers(rawCodes);

        List<String> appliedCodes = new ArrayList<>();
        Map<String, Double> itemDiscounts = new HashMap<>();
        Map<String, Double> itemDiscountPercentages = new HashMap<>();

        // =========================================================================
        // 1. NHÁNH PHÍ GIAO HÀNG (SHIPPING FEE DISCOUNT) - TOÀN ĐƠN (ALL)
        // =========================================================================
        double shippingDiscountAmount = 0.0;
        if (ShippingMethod.PICKUP == shippingMethod) {
            shippingDiscountAmount = rawShippingFee; // Nhận tại kho Định Hòa -> Free ship 100%
        } else {
            // Kiểm tra các mã FreeShip trong request
            for (String code : rawCodes) {
                if (code == null) continue;
                String upperCode = code.trim().toUpperCase();
                com.ddicg.erp.modules.order.dto.VoucherCacheDto voucher = voucherMap.get(upperCode);

                if (voucher != null && voucher.getVoucherType() == VoucherType.SHIPPING) {
                    if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                        double discount = rawShippingFee * (voucher.getDiscountValue() / 100.0);
                        if (voucher.getMaxDiscountAmount() != null) {
                            discount = Math.min(discount, voucher.getMaxDiscountAmount());
                        }
                        shippingDiscountAmount = Math.min(rawShippingFee, shippingDiscountAmount + discount);
                    } else {
                        shippingDiscountAmount = Math.min(rawShippingFee, shippingDiscountAmount + voucher.getDiscountValue());
                    }
                    appliedCodes.add(upperCode);
                } else if (upperCode.equals("FREESHIP") || upperCode.equals("MIENPHISHIP") || upperCode.equals("FREE_SHIP")) {
                    shippingDiscountAmount = rawShippingFee;
                    appliedCodes.add(upperCode);
                } else if (upperCode.startsWith("FREESHIP") && upperCode.endsWith("K")) {
                    try {
                        String numPart = upperCode.replace("FREESHIP", "").replace("K", "");
                        double fixedVal = Double.parseDouble(numPart) * 1000.0;
                        shippingDiscountAmount = Math.min(rawShippingFee, shippingDiscountAmount + fixedVal);
                        appliedCodes.add(upperCode);
                    } catch (Exception ignored) {}
                }
            }
        }

        // =========================================================================
        // 2. NHÁNH TIỀN HÀNG (PRODUCT DISCOUNT) - BÓC TÁCH THEO TỪNG ATTRIBUTES
        //    (CỦA MÓN NÀO CHỈ TRỪ VÀO MÓN ĐÓ, CỘNG DỒN ƯU ĐÃI TRÊN CÙNG 1 MÓN)
        // =========================================================================
        double totalProductDiscount = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (CreateOrderRequest.OrderItemRequest itemReq : items) {
            String sku = itemReq.getAttributesSku();
            int qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : 1;
            Attributes attr = attrMap.get(sku);

            if (attr == null) continue;

            double unitPrice = attr.getPrice();
            double salePrice = attr.getSalePrice();
            double originalLinePrice = unitPrice * qty;
            double lineDiscount = 0.0;

            // Ưu đãi Lớp 1: Khuyến mãi từ Attributes.promotions (Flash Sale / Sự kiện thời vụ)
            if (attr.getPromotions() != null && !attr.getPromotions().isEmpty()) {
                for (Promotion promo : attr.getPromotions()) {
                    if (promo.getDiscountPercent() != null && promo.getDiscountPercent() > 0) {
                        boolean validTime = (promo.getStartDate() == null || !now.isBefore(promo.getStartDate()))
                                && (promo.getEndDate() == null || !now.isAfter(promo.getEndDate()));
                        if (validTime) {
                            lineDiscount += originalLinePrice * (promo.getDiscountPercent() / 100.0);
                        }
                    }
                }
            }

            // Ưu đãi Lớp 2: Chênh lệch giá gốc vs salePrice (nếu chưa có promotion %)
            if (lineDiscount == 0.0 && salePrice > 0 && unitPrice > salePrice) {
                lineDiscount += (unitPrice - salePrice) * qty;
            }

            // Ưu đãi Lớp 3: Voucher sản phẩm trong discountCodes (áp dụng cho SKU này hoặc toàn bộ sản phẩm)
            for (String code : rawCodes) {
                if (code == null) continue;
                String upperCode = code.trim().toUpperCase();
                com.ddicg.erp.modules.order.dto.VoucherCacheDto voucher = voucherMap.get(upperCode);

                if (voucher != null && voucher.getVoucherType() == VoucherType.PRODUCT) {
                    boolean isApplicable = voucher.getApplicableSkus() == null
                            || voucher.getApplicableSkus().isEmpty()
                            || voucher.getApplicableSkus().contains(sku);

                    if (isApplicable) {
                        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                            double voucherDiscount = originalLinePrice * (voucher.getDiscountValue() / 100.0);
                            if (voucher.getMaxDiscountAmount() != null) {
                                voucherDiscount = Math.min(voucherDiscount, voucher.getMaxDiscountAmount());
                            }
                            lineDiscount += voucherDiscount;
                        } else {
                            lineDiscount += voucher.getDiscountValue();
                        }
                        if (!appliedCodes.contains(upperCode)) appliedCodes.add(upperCode);
                    }
                } else if (upperCode.equals("GIAM10") || upperCode.equals("SALE10")) {
                    lineDiscount += originalLinePrice * 0.10;
                    if (!appliedCodes.contains(upperCode)) appliedCodes.add(upperCode);
                } else if (upperCode.equals("50K") || upperCode.equals("GIAM50K")) {
                    lineDiscount += 50000.0;
                    if (!appliedCodes.contains(upperCode)) appliedCodes.add(upperCode);
                }
            }

            // Ràng buộc an toàn: Tiền giảm tối đa bằng chính giá gốc của món đó
            lineDiscount = Math.min(originalLinePrice, lineDiscount);
            double percentage = originalLinePrice > 0 ? (lineDiscount / originalLinePrice) * 100.0 : 0.0;

            itemDiscounts.put(sku, lineDiscount);
            itemDiscountPercentages.put(sku, percentage);
            totalProductDiscount += lineDiscount;
        }

        return DiscountEvaluationResult.builder()
                .productDiscountAmount(totalProductDiscount)
                .shippingDiscountAmount(shippingDiscountAmount)
                .appliedDiscountCodes(appliedCodes)
                .itemDiscounts(itemDiscounts)
                .itemDiscountPercentages(itemDiscountPercentages)
                .description("Tính cước giao hàng độc lập toàn đơn và giảm giá bóc tách theo từng Attributes")
                .build();
    }
}
