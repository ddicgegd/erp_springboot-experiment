package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
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
            if (attr != null && attr.getSku() != null && attr.getSku().getSku() != null) {
                attrMap.put(attr.getSku().getSku(), attr);
            }
        }

        // Lấy danh sách Voucher hợp lệ trực tiếp từ Redis Cache
        Map<String, VoucherCacheDto> voucherMap = voucherRedisService.getValidVouchers(rawCodes);
        List<String> appliedCodes = new ArrayList<>();

        // =========================================================================
        // NHÁNH 3: PHÍ VẬN CHUYỂN (SHIPPING DISCOUNT) - ÁP DỤNG 1 LẦN TRONG 1 ORDER
        // =========================================================================
        double shippingDiscountAmount = evaluateShippingDiscount(rawShippingFee, shippingMethod, rawCodes, voucherMap, appliedCodes);

        // =========================================================================
        // NHÁNH 2: THEO TỪNG SẢN PHẨM / ATTRIBUTES (PRODUCT_ITEM DISCOUNT)
        //          (MỖI SẢN PHẨM CHỈ ĐƯỢC ÁP DỤNG TỐI ĐA 1 VOUCHER ITEM)
        // =========================================================================
        Map<String, Double> itemDiscounts = new HashMap<>();
        Map<String, Double> itemNetSubtotals = new HashMap<>();
        Map<String, Double> itemOriginalPrices = new HashMap<>();

        double totalItemLevelDiscount = evaluateItemDiscounts(
                items,
                attrMap,
                rawCodes,
                voucherMap,
                appliedCodes,
                itemDiscounts,
                itemNetSubtotals,
                itemOriginalPrices
        );

        // =========================================================================
        // NHÁNH 1: TOÀN BỘ ĐƠN HÀNG (GLOBAL_ORDER DISCOUNT)
        //          (ÁP DỤNG TRÊN TỔNG TIỀN HÀNG CÒN LẠI & PHÂN BỔ XUỐNG TỪNG ITEM)
        // =========================================================================
        double globalDiscountAmount = evaluateGlobalDiscount(
                rawCodes,
                voucherMap,
                appliedCodes,
                itemNetSubtotals,
                itemDiscounts,
                itemOriginalPrices
        );

        // Tính % giảm giá tổng hợp trên từng SKU
        Map<String, Double> itemDiscountPercentages = new HashMap<>();
        for (Map.Entry<String, Double> entry : itemDiscounts.entrySet()) {
            String sku = entry.getKey();
            double totalLineDiscount = entry.getValue();
            double origLinePrice = itemOriginalPrices.getOrDefault(sku, 0.0);
            double percentage = origLinePrice > 0 ? (totalLineDiscount / origLinePrice) * 100.0 : 0.0;
            itemDiscountPercentages.put(sku, percentage);
        }

        double totalProductDiscount = totalItemLevelDiscount + globalDiscountAmount;

        return DiscountEvaluationResult.builder()
                .productDiscountAmount(totalProductDiscount)
                .itemLevelDiscountAmount(totalItemLevelDiscount)
                .globalDiscountAmount(globalDiscountAmount)
                .shippingDiscountAmount(shippingDiscountAmount)
                .appliedDiscountCodes(appliedCodes)
                .itemDiscounts(itemDiscounts)
                .itemDiscountPercentages(itemDiscountPercentages)
                .description("Xử lý chiết khấu 3 nhánh: Phí vận chuyển (độc lập 1 lần), Theo từng sản phẩm (tối đa 1 voucher/món) và Toàn bộ đơn hàng")
                .build();
    }

    /**
     * Nhánh 3: Tính giảm giá cước vận chuyển (Áp dụng đúng 1 lần / order, không làm cước ship âm)
     */
    private double evaluateShippingDiscount(
            Double rawShippingFee,
            ShippingMethod shippingMethod,
            List<String> rawCodes,
            Map<String, VoucherCacheDto> voucherMap,
            List<String> appliedCodes
    ) {
        if (rawShippingFee == null || rawShippingFee <= 0.0) {
            return 0.0;
        }

        // Nhận tại kho -> Miễn phí vận chuyển 100%
        if (ShippingMethod.PICKUP == shippingMethod) {
            return rawShippingFee;
        }

        // Tìm đúng 1 mã Shipping hợp lệ đầu tiên
        for (String code : rawCodes) {
            if (code == null || code.isBlank()) continue;
            String upperCode = code.trim().toUpperCase();
            VoucherCacheDto voucher = voucherMap.get(upperCode);

            if (voucher != null && voucher.getVoucherType() == VoucherType.SHIPPING) {
                double discount;
                if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                    discount = rawShippingFee * (voucher.getDiscountValue() / 100.0);
                    if (voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount() > 0) {
                        discount = Math.min(discount, voucher.getMaxDiscountAmount());
                    }
                } else {
                    discount = voucher.getDiscountValue();
                }
                appliedCodes.add(upperCode);
                return Math.min(rawShippingFee, Math.max(0.0, discount));
            } else if (upperCode.equals("FREESHIP") || upperCode.equals("MIENPHISHIP") || upperCode.equals("FREE_SHIP")) {
                appliedCodes.add(upperCode);
                return rawShippingFee;
            } else if (upperCode.startsWith("FREESHIP") && upperCode.endsWith("K")) {
                try {
                    String numPart = upperCode.replace("FREESHIP", "").replace("K", "");
                    double fixedVal = Double.parseDouble(numPart) * 1000.0;
                    appliedCodes.add(upperCode);
                    return Math.min(rawShippingFee, Math.max(0.0, fixedVal));
                } catch (Exception ignored) {}
            }
        }

        return 0.0;
    }

    /**
     * Nhánh 2: Tính giảm giá theo từng sản phẩm (Flash Sale + Giá Sale + Đúng 1 Voucher PRODUCT_ITEM đầu tiên)
     */
    private double evaluateItemDiscounts(
            List<CreateOrderRequest.OrderItemRequest> items,
            Map<String, Attributes> attrMap,
            List<String> rawCodes,
            Map<String, VoucherCacheDto> voucherMap,
            List<String> appliedCodes,
            Map<String, Double> itemDiscounts,
            Map<String, Double> itemNetSubtotals,
            Map<String, Double> itemOriginalPrices
    ) {
        double totalItemLevelDiscount = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (CreateOrderRequest.OrderItemRequest itemReq : items) {
            if (itemReq == null || itemReq.getAttributesSku() == null) continue;
            String sku = itemReq.getAttributesSku();
            int qty = (itemReq.getQuantity() != null && itemReq.getQuantity() > 0) ? itemReq.getQuantity() : 1;
            Attributes attr = attrMap.get(sku);

            if (attr == null) continue;

            double unitPrice = attr.getPrice();
            double salePrice = attr.getSalePrice();
            double originalLinePrice = unitPrice * qty;
            double lineDiscount = 0.0;

            // Ưu đãi Lớp 1: Khuyến mãi từ Attributes.promotions (Flash Sale / Sự kiện)
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

            // Ưu đãi Lớp 2: Chênh lệch giá niêm yết vs giá bán lẻ (nếu chưa có Flash Sale %)
            if (lineDiscount == 0.0 && salePrice > 0 && unitPrice > salePrice) {
                lineDiscount += (unitPrice - salePrice) * qty;
            }

            // Ưu đãi Lớp 3: Áp dụng ĐÚNG 1 VOUCHER PRODUCT_ITEM HỢP LỆ ĐẦU TIÊN cho SKU này
            for (String code : rawCodes) {
                if (code == null || code.isBlank()) continue;
                String upperCode = code.trim().toUpperCase();
                VoucherCacheDto voucher = voucherMap.get(upperCode);

                if (voucher != null && voucher.getVoucherType() == VoucherType.PRODUCT_ITEM) {
                    boolean isApplicable = voucher.getApplicableSkus() == null
                            || voucher.getApplicableSkus().isEmpty()
                            || voucher.getApplicableSkus().contains(sku);

                    if (isApplicable) {
                        double voucherDiscount;
                        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                            voucherDiscount = originalLinePrice * (voucher.getDiscountValue() / 100.0);
                            if (voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount() > 0) {
                                voucherDiscount = Math.min(voucherDiscount, voucher.getMaxDiscountAmount());
                            }
                        } else {
                            voucherDiscount = voucher.getDiscountValue();
                        }

                        lineDiscount += voucherDiscount;
                        if (!appliedCodes.contains(upperCode)) {
                            appliedCodes.add(upperCode);
                        }
                        // Chỉ áp dụng đúng 1 voucher item cho sản phẩm này -> Dừng tìm kiếm voucher item
                        break;
                    }
                }
            }

            // Ràng buộc an toàn: Tiền giảm tối đa bằng chính giá gốc của dòng sản phẩm đó
            lineDiscount = Math.min(originalLinePrice, Math.max(0.0, lineDiscount));
            double lineNetSubtotal = Math.max(0.0, originalLinePrice - lineDiscount);

            itemOriginalPrices.put(sku, originalLinePrice);
            itemDiscounts.put(sku, lineDiscount);
            itemNetSubtotals.put(sku, lineNetSubtotal);
            totalItemLevelDiscount += lineDiscount;
        }

        return totalItemLevelDiscount;
    }

    /**
     * Nhánh 1: Tính giảm giá toàn bộ đơn hàng (GLOBAL_ORDER) trên tổng tiền hàng còn lại và phân bổ xuống từng OrderItem
     */
    private double evaluateGlobalDiscount(
            List<String> rawCodes,
            Map<String, VoucherCacheDto> voucherMap,
            List<String> appliedCodes,
            Map<String, Double> itemNetSubtotals,
            Map<String, Double> itemDiscounts,
            Map<String, Double> itemOriginalPrices
    ) {
        double netSubtotal = itemNetSubtotals.values().stream().mapToDouble(Double::doubleValue).sum();
        if (netSubtotal <= 0.0) {
            return 0.0;
        }

        double globalDiscount = 0.0;
        String appliedGlobalCode = null;

        // Tìm ĐÚNG 1 VOUCHER GLOBAL_ORDER HỢP LỆ ĐẦU TIÊN
        for (String code : rawCodes) {
            if (code == null || code.isBlank()) continue;
            String upperCode = code.trim().toUpperCase();
            VoucherCacheDto voucher = voucherMap.get(upperCode);

            if (voucher != null && voucher.getVoucherType() == VoucherType.GLOBAL_ORDER) {
                if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
                    globalDiscount = netSubtotal * (voucher.getDiscountValue() / 100.0);
                    if (voucher.getMaxDiscountAmount() != null && voucher.getMaxDiscountAmount() > 0) {
                        globalDiscount = Math.min(globalDiscount, voucher.getMaxDiscountAmount());
                    }
                } else {
                    globalDiscount = voucher.getDiscountValue();
                }
                appliedGlobalCode = upperCode;
                break;
            } else if (upperCode.equals("GIAM10") || upperCode.equals("SALE10")) {
                globalDiscount = netSubtotal * 0.10;
                appliedGlobalCode = upperCode;
                break;
            } else if (upperCode.equals("50K") || upperCode.equals("GIAM50K")) {
                globalDiscount = 50000.0;
                appliedGlobalCode = upperCode;
                break;
            }
        }

        if (appliedGlobalCode != null && !appliedCodes.contains(appliedGlobalCode)) {
            appliedCodes.add(appliedGlobalCode);
        }

        // Ràng buộc an toàn: Tiền giảm toàn đơn không vượt quá tổng tiền hàng còn lại
        globalDiscount = Math.min(netSubtotal, Math.max(0.0, globalDiscount));

        // Phân bổ tỷ lệ số tiền giảm toàn đơn xuống từng OrderItem
        if (globalDiscount > 0.0 && netSubtotal > 0.0) {
            for (Map.Entry<String, Double> entry : itemNetSubtotals.entrySet()) {
                String sku = entry.getKey();
                double lineNet = entry.getValue();
                double allocated = (lineNet / netSubtotal) * globalDiscount;

                double currentItemDiscount = itemDiscounts.getOrDefault(sku, 0.0);
                double originalPrice = itemOriginalPrices.getOrDefault(sku, 0.0);

                double totalItemDiscount = Math.min(originalPrice, currentItemDiscount + allocated);
                itemDiscounts.put(sku, totalItemDiscount);
            }
        }

        return globalDiscount;
    }
}

