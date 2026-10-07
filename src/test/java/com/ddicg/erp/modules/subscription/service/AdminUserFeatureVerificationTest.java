package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.cart.model.CartContext;
import com.ddicg.erp.modules.cart.storage.CartStorageFactory;
import com.ddicg.erp.modules.cart.storage.CartStorageStrategy;
import com.ddicg.erp.modules.cart.storage.PersistentDbCartStorage;
import com.ddicg.erp.modules.cart.storage.RedisCartStorage;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.modules.order.service.discount.DefaultOrderDiscountProcessor;
import com.ddicg.erp.modules.order.service.discount.DiscountEvaluationResult;
import com.ddicg.erp.modules.order.service.discount.OrderDiscountContext;
import com.ddicg.erp.modules.order.service.discount.VoucherRedisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserFeatureVerificationTest {

    @Mock
    private RedisCartStorage redisCartStorage;

    @Mock
    private PersistentDbCartStorage persistentDbCartStorage;

    @Mock
    private VoucherRedisService voucherRedisService;

    @InjectMocks
    private DefaultOrderDiscountProcessor discountProcessor;

    @Test
    @DisplayName("1. Kiểm tra Giỏ hàng của ADMIN (DIAMOND): Lưu trữ DB vĩnh viễn và không giới hạn sản phẩm")
    void testAdminCartUnlimitedAndPersistentDb() {
        User adminUser = User.builder()
                .id(1L)
                .email("ADMIN@gmail.com")
                .name("ADMIN")
                .rank(UserRank.DIAMOND)
                .build();

        CartContext ctx = CartContext.builder()
                .user(adminUser)
                .isGuest(false)
                .key("cart_user_1")
                .ownerName("ADMIN")
                .build();

        CartStorageFactory factory = new CartStorageFactory(redisCartStorage, persistentDbCartStorage);
        CartStorageStrategy strategy = factory.getStrategy(ctx);

        // Phải dùng PersistentDbCartStorage cho DIAMOND (lưu DB vĩnh viễn)
        assertThat(strategy).isSameAs(persistentDbCartStorage);
        assertThat(ctx.isVip()).isTrue();
    }

    @Test
    @DisplayName("2. Kiểm tra Discount của ADMIN (DIAMOND): Tự động Free Ship 100% & Giảm trực tiếp 3% toàn đơn")
    void testAdminDiscountFreeShippingAnd3Percent() {
        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Collections.emptyMap());

        Attributes attrLaptop = Attributes.builder()
                .sku(SkuInfo.builder().sku("LAPTOP-PRO").build())
                .price(20000000.0)
                .salePrice(20000000.0)
                .build();

        CreateOrderRequest.OrderItemRequest item = CreateOrderRequest.OrderItemRequest.builder()
                .attributesSku("LAPTOP-PRO")
                .quantity(1)
                .build();

        OrderDiscountContext context = OrderDiscountContext.builder()
                .customerId("1")
                .userRank(UserRank.DIAMOND)
                .shippingMethod(ShippingMethod.DELIVERY)
                .rawShippingFee(60000.0) // 60.000 đ ship
                .items(List.of(item))
                .attributesList(List.of(attrLaptop))
                .discountCodes(Collections.emptyList())
                .build();

        DiscountEvaluationResult result = discountProcessor.evaluateDiscount(context);

        // Free ship 100%
        assertThat(result.getShippingDiscountAmount()).isEqualTo(60000.0);
        assertThat(result.getAppliedDiscountCodes()).contains("MA_THUAT_FREE_SHIPPING");

        // Giảm 3% trên 20.000.000 đ = 600.000 đ
        assertThat(result.getGlobalDiscountAmount()).isEqualTo(600000.0);
        assertThat(result.getAppliedDiscountCodes()).contains("MA_THUAT_3%_DISCOUNT");
    }
}
