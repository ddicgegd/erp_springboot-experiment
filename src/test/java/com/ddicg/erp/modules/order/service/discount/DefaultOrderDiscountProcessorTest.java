package com.ddicg.erp.modules.order.service.discount;

import com.ddicg.erp.core.common.model.embedded.Promotion;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.DiscountType;
import com.ddicg.erp.core.common.model.enums.VoucherType;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.modules.order.model.Voucher;
import com.ddicg.erp.modules.order.repository.VoucherRepository;
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
                .salePrice(180000.0)
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
    @DisplayName("Test 1: Giảm cước giao hàng độc lập toàn đơn với mã FREESHIP")
    void testShippingDiscount_IndependentAll() {
        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(500000.0)
                .rawShippingFee(35000.0)
                .discountCodes(List.of("FREESHIP"))
                .shippingMethod("DELIVERY")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-B").quantity(1).build()
                ))
                .attributesList(List.of(attrB))
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Collections.emptyMap());

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        assertEquals(35000.0, result.getShippingDiscountAmount()); // Giảm hết phí ship
        assertEquals(0.0, result.getProductDiscountAmount()); // Tiền hàng không đổi vì SKU-B không có giảm giá
        assertTrue(result.getAppliedDiscountCodes().contains("FREESHIP"));
    }

    @Test
    @DisplayName("Test 2: Của món nào chỉ trừ vào món đó (SKU-A giảm 10%, SKU-B giữ nguyên)")
    void testProductDiscount_PerItemIndependence() {
        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(500000.0)
                .rawShippingFee(35000.0)
                .discountCodes(Collections.emptyList())
                .shippingMethod("DELIVERY")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-A").quantity(1).build(),
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-B").quantity(1).build()
                ))
                .attributesList(List.of(attrA, attrB))
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Collections.emptyMap());

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        assertEquals(20000.0, result.getItemDiscounts().get("SKU-A")); // 10% của 200k = 20k
        assertEquals(0.0, result.getItemDiscounts().getOrDefault("SKU-B", 0.0)); // SKU-B không giảm
        assertEquals(20000.0, result.getProductDiscountAmount());
        assertEquals(0.0, result.getShippingDiscountAmount());
    }

    @Test
    @DisplayName("Test 3: Kết hợp cả Voucher Ship toàn đơn và Voucher Sản phẩm cộng dồn")
    void testCombine_ShippingVoucher_And_ProductVoucher() {
        com.ddicg.erp.modules.order.dto.VoucherCacheDto shipVoucher = com.ddicg.erp.modules.order.dto.VoucherCacheDto.builder()
                .code("V_SHIP")
                .voucherType(VoucherType.SHIPPING)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(20000.0)
                .build();

        com.ddicg.erp.modules.order.dto.VoucherCacheDto prodVoucher = com.ddicg.erp.modules.order.dto.VoucherCacheDto.builder()
                .code("V_PROD")
                .voucherType(VoucherType.PRODUCT)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(10.0) // Giảm thêm 10%
                .build();

        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(java.util.Map.of("V_SHIP", shipVoucher, "V_PROD", prodVoucher));

        OrderDiscountContext context = OrderDiscountContext.builder()
                .subtotal(500000.0)
                .rawShippingFee(35000.0)
                .discountCodes(List.of("V_SHIP", "V_PROD"))
                .shippingMethod("DELIVERY")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-A").quantity(1).build(),
                        CreateOrderRequest.OrderItemRequest.builder().attributesSku("SKU-B").quantity(1).build()
                ))
                .attributesList(List.of(attrA, attrB))
                .build();

        DiscountEvaluationResult result = processor.evaluateDiscount(context);

        assertNotNull(result);
        // Ship giảm 20k
        assertEquals(20000.0, result.getShippingDiscountAmount());
        // SKU-A có Promotion sẵn 10% (20k) + Voucher toàn bộ sản phẩm thêm 10% (20k) = 40k
        assertEquals(40000.0, result.getItemDiscounts().get("SKU-A"));
        // SKU-B được giảm 10% từ voucher toàn bộ sản phẩm = 30k
        assertEquals(30000.0, result.getItemDiscounts().get("SKU-B"));
        // Tổng giảm hàng = 40k + 30k = 70k
        assertEquals(70000.0, result.getProductDiscountAmount());
        assertTrue(result.getAppliedDiscountCodes().contains("V_SHIP"));
        assertTrue(result.getAppliedDiscountCodes().contains("V_PROD"));
    }
}
