package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.dto.VoucherCacheDto;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultOrderDiscountProcessorTest {

    @Mock
    private VoucherRedisService voucherRedisService;

    @InjectMocks
    private DefaultOrderDiscountProcessor processor;

    private Attributes attrA;
    private Attributes attrB;

    @BeforeEach
    void setUp() {
        attrA = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-A").build())
                .name("Product A")
                .price(200000.0)
                .salePrice(200000.0)
                .promotions(List.of(
                        Promotion.builder()
                                .name("Flash Sale 10%")
                                .discountPercent(10.0)
                                .startDate(LocalDateTime.now().minusDays(1))
                                .endDate(LocalDateTime.now().plusDays(1))
                                .build()
                ))
                .build();

        attrB = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-B").build())
                .name("Product B")
                .price(300000.0)
                .salePrice(300000.0)
                .promotions(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("Nhánh 3: Giảm cước giao hàng độc lập toàn đơn với mã FREESHIP & chỉ áp dụng 1 mã ship")
    void testShippingDiscount_IndependentSingleVoucher() {
        VoucherCacheDto shipVoucher1 = VoucherCacheDto.builder()
                .code("SHIP20")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(20000.0)
                .build();

        VoucherCacheDto shipVoucher2 = VoucherCacheDto.builder()
                .code("SHIP10")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(10000.0)
                .build();

        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(500000.0)
                .rawShippingFee(35000.0)
                .discountCodes(List.of("SHIP20", "SHIP10"))
                .shippingMethod(ShippingMethod.DELIVERY)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-B").quantity(1).build()
                ))
                .attributesList(List.of(attrB))
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Map.of("SHIP20", shipVoucher1, "SHIP10", shipVoucher2));

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        assertEquals(20000.0, result.getShippingDiscountAmount()); // Chỉ áp dụng mã ship đầu tiên (20k)
        assertEquals(0.0, result.getProductDiscountAmount()); // Tiền hàng không đổi
        assertTrue(result.getAppliedDiscountCodes().contains("SHIP20"));
        assertFalse(result.getAppliedDiscountCodes().contains("SHIP10"));
    }

    @Test
    @DisplayName("Nhánh 3: Nhận hàng tại kho (PICKUP) tự động miễn phí vận chuyển 100%")
    void testShippingDiscount_Pickup_Free100Percent() {
        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(300000.0)
                .rawShippingFee(50000.0)
                .discountCodes(Collections.emptyList())
                .shippingMethod(ShippingMethod.PICKUP)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-B").quantity(1).build()
                ))
                .attributesList(List.of(attrB))
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Collections.emptyMap());

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        assertEquals(50000.0, result.getShippingDiscountAmount());
    }

    @Test
    @DisplayName("Nhánh 2: 1 sản phẩm chỉ được tính tối đa 1 voucher item (chọn mã hợp lệ đầu tiên)")
    void testProductItemDiscount_SingleVoucherPerItem() {
        // Tạo 2 voucher cùng áp dụng cho SKU-A
        VoucherCacheDto itemVoucher1 = VoucherCacheDto.builder()
                .code("ITEM_A1")
                .voucherType(VoucherType.PRODUCT_ITEM)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(30000.0)
                .applicableSkus(List.of("SKU-A"))
                .build();

        VoucherCacheDto itemVoucher2 = VoucherCacheDto.builder()
                .code("ITEM_A2")
                .voucherType(VoucherType.PRODUCT_ITEM)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(50000.0)
                .applicableSkus(List.of("SKU-A"))
                .build();

        // Không dùng Flash Sale để test riêng voucher item
        Attributes plainAttrA = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-A").build())
                .name("Product A")
                .price(200000.0)
                .salePrice(200000.0)
                .promotions(Collections.emptyList())
                .build();

        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(200000.0)
                .rawShippingFee(30000.0)
                .discountCodes(List.of("ITEM_A1", "ITEM_A2"))
                .shippingMethod(ShippingMethod.DELIVERY)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-A").quantity(1).build()
                ))
                .attributesList(List.of(plainAttrA))
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Map.of("ITEM_A1", itemVoucher1, "ITEM_A2", itemVoucher2));

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        // SKU-A chỉ nhận đúng 1 voucher item đầu tiên là ITEM_A1 (30k), không bị cộng dồn ITEM_A2 (50k)
        assertEquals(30000.0, result.getItemDiscounts().get("SKU-A"));
        assertEquals(30000.0, result.getProductDiscountAmount());
        assertEquals(30000.0, result.getItemLevelDiscountAmount());
        assertEquals(0.0, result.getGlobalDiscountAmount());
        assertTrue(result.getAppliedDiscountCodes().contains("ITEM_A1"));
        assertFalse(result.getAppliedDiscountCodes().contains("ITEM_A2"));
    }

    @Test
    @DisplayName("Nhánh 1 & Nhánh 2 & Nhánh 3: Kết hợp cả Voucher Item + Voucher Toàn Đơn + Voucher Ship")
    void testCombine_AllThreeBranches() {
        // Voucher Ship
        VoucherCacheDto shipVoucher = VoucherCacheDto.builder()
                .code("V_SHIP")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(25000.0)
                .build();

        // Voucher Item cho SKU-A (giảm 20k)
        VoucherCacheDto itemVoucher = VoucherCacheDto.builder()
                .code("V_ITEM_A")
                .voucherType(VoucherType.PRODUCT_ITEM)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(20000.0)
                .applicableSkus(List.of("SKU-A"))
                .build();

        // Voucher Toàn Đơn (GLOBAL_ORDER - giảm 10% trên Net Subtotal)
        VoucherCacheDto globalVoucher = VoucherCacheDto.builder()
                .code("V_GLOBAL")
                .voucherType(VoucherType.GLOBAL_ORDER)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(10.0)
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Map.of(
                "V_SHIP", shipVoucher,
                "V_ITEM_A", itemVoucher,
                "V_GLOBAL", globalVoucher
        ));

        // SKU-A: Giá 200k, Flash sale 10% (20k) + Voucher Item (20k) = Giảm item 40k -> Net SKU-A = 160k
        // SKU-B: Giá 300k -> Net SKU-B = 300k
        // Tổng Net Subtotal = 160k + 300k = 460k
        // Voucher Toàn Đơn 10% = 46k
        // Phân bổ:
        // SKU-A nhận: 46k * (160k / 460k) = 16k -> Tổng giảm SKU-A = 40k + 16k = 56k
        // SKU-B nhận: 46k * (300k / 460k) = 30k -> Tổng giảm SKU-B = 0k + 30k = 30k
        // Tổng product discount = 56k + 30k = 86k (Item level: 40k, Global: 46k)
        // Ship discount = 25k

        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(500000.0)
                .rawShippingFee(30000.0)
                .discountCodes(List.of("V_SHIP", "V_ITEM_A", "V_GLOBAL"))
                .shippingMethod(ShippingMethod.DELIVERY)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-A").quantity(1).build(),
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-B").quantity(1).build()
                ))
                .attributesList(List.of(attrA, attrB))
                .build();

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        assertEquals(25000.0, result.getShippingDiscountAmount());
        assertEquals(40000.0, result.getItemLevelDiscountAmount());
        assertEquals(46000.0, result.getGlobalDiscountAmount());
        assertEquals(86000.0, result.getProductDiscountAmount());

        assertEquals(56000.0, result.getItemDiscounts().get("SKU-A"), 0.001);
        assertEquals(30000.0, result.getItemDiscounts().get("SKU-B"), 0.001);

        assertTrue(result.getAppliedDiscountCodes().contains("V_SHIP"));
        assertTrue(result.getAppliedDiscountCodes().contains("V_ITEM_A"));
        assertTrue(result.getAppliedDiscountCodes().contains("V_GLOBAL"));
    }
}

