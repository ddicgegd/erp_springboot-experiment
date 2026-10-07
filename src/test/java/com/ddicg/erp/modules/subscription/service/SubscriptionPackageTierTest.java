package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.modules.order.service.discount.DefaultOrderDiscountProcessor;
import com.ddicg.erp.modules.order.service.discount.DiscountEvaluationResult;
import com.ddicg.erp.modules.order.service.discount.OrderDiscountContext;
import com.ddicg.erp.modules.order.service.discount.VoucherRedisService;
import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import com.ddicg.erp.modules.payment.repository.CustomerPaymentProfileRepository;
import com.ddicg.erp.modules.payment.service.client.PaymentGatewayClient;
import com.ddicg.erp.modules.subscription.dto.request.GooglePayWebRegisterRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.UserSubscription;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.repository.SubscriptionPlanRepository;
import com.ddicg.erp.modules.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionPackageTierTest {

    @Mock
    private PaymentGatewayClient paymentGatewayClient;
    @Mock
    private CustomerPaymentProfileRepository profileRepository;
    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FineractJournalService fineractJournalService;

    @InjectMocks
    private GooglePayWebService googlePayWebService;

    @Mock
    private VoucherRedisService voucherRedisService;

    @InjectMocks
    private DefaultOrderDiscountProcessor discountProcessor;

    @Test
    @DisplayName("Gói Ánh Kim: 30k/tháng, 7 ngày free trial -> Kích hoạt TRIALING, không trừ tiền ngay")
    void testRegisterAnhKimPlanWithTrial() {
        Long userId = 100L;
        User user = User.builder().id(userId).rank(UserRank.MEMBER).build();
        SubscriptionPlan planAnhKim = SubscriptionPlan.builder()
                .id(1L)
                .planCode("ANH_KIM")
                .name("Gói Ánh Kim")
                .targetRank("SILVER")
                .billingPeriodMonths(1)
                .freeTrialDays(7)
                .priceAmount(new BigDecimal("30000.00"))
                .currency("VND")
                .build();

        GooglePayWebRegisterRequest request = new GooglePayWebRegisterRequest();
        request.setPlanCode("ANH_KIM");
        request.setPaymentToken("tok_gpay_anh_kim");
        request.setCardNetwork("VISA");
        request.setLastFour("1234");

        CustomerPaymentProfile profile = CustomerPaymentProfile.builder()
                .profileCode("PROF_100")
                .multiUseToken("tok_vault_100")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("ANH_KIM")).thenReturn(Optional.of(planAnhKim));
        when(userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());
        when(paymentGatewayClient.vaultPaymentMethod(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(profile);
        when(profileRepository.save(any())).thenReturn(profile);
        when(userSubscriptionRepository.save(any())).thenAnswer(inv -> {
            UserSubscription sub = inv.getArgument(0);
            sub.setId(10L);
            return sub;
        });

        UserSubscriptionResponse response = googlePayWebService.registerTrialSubscription(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getPlanCode()).isEqualTo("ANH_KIM");
        assertThat(response.getTargetRank()).isEqualTo("SILVER");
        assertThat(response.getSubscriptionState()).isEqualTo(SubscriptionState.TRIALING);
        assertThat(response.getIsTrial()).isTrue();
        // Không gọi charge tiền khi đang dùng thử
        verify(paymentGatewayClient, never()).chargeRecurringMit(anyString(), any(), anyString(), anyString());
        // Rank được nâng lên SILVER
        assertThat(user.getRank()).isEqualTo("SILVER");
    }

    @Test
    @DisplayName("Gói Ma Thuật: 120k/tháng, no free trial -> Thu tiền ngay 120k, hạch toán Fineract, kích hoạt ACTIVE")
    void testRegisterMaThuatPlanNoTrialImmediateCharge() {
        Long userId = 200L;
        User user = User.builder().id(userId).rank(UserRank.MEMBER).build();
        SubscriptionPlan planMaThuat = SubscriptionPlan.builder()
                .id(2L)
                .planCode("MA_THUAT")
                .name("Gói Ma Thuật")
                .targetRank("DIAMOND")
                .billingPeriodMonths(1)
                .freeTrialDays(0) // No trial
                .priceAmount(new BigDecimal("120000.00"))
                .currency("VND")
                .build();

        GooglePayWebRegisterRequest request = new GooglePayWebRegisterRequest();
        request.setPlanCode("MA_THUAT");
        request.setPaymentToken("tok_gpay_ma_thuat");
        request.setCardNetwork("MASTERCARD");
        request.setLastFour("5678");

        CustomerPaymentProfile profile = CustomerPaymentProfile.builder()
                .profileCode("PROF_200")
                .multiUseToken("tok_vault_200")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(planRepository.findByPlanCode("MA_THUAT")).thenReturn(Optional.of(planMaThuat));
        when(userSubscriptionRepository.findByUserIdAndSubscriptionStateIn(eq(userId), anyList()))
                .thenReturn(Optional.empty());
        when(paymentGatewayClient.vaultPaymentMethod(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(profile);
        when(profileRepository.save(any())).thenReturn(profile);
        when(paymentGatewayClient.chargeRecurringMit(eq("tok_vault_200"), eq(new BigDecimal("120000.00")), eq("VND"), anyString()))
                .thenReturn(new PaymentGatewayClient.MitChargeResult(true, "TXN_120K", null, null));
        when(userSubscriptionRepository.save(any())).thenAnswer(inv -> {
            UserSubscription sub = inv.getArgument(0);
            sub.setId(20L);
            return sub;
        });

        UserSubscriptionResponse response = googlePayWebService.registerTrialSubscription(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getPlanCode()).isEqualTo("MA_THUAT");
        assertThat(response.getTargetRank()).isEqualTo("DIAMOND");
        assertThat(response.getSubscriptionState()).isEqualTo(SubscriptionState.ACTIVE);
        assertThat(response.getIsTrial()).isFalse();
        // Thu tiền ngay lập tức
        verify(paymentGatewayClient).chargeRecurringMit(eq("tok_vault_200"), eq(new BigDecimal("120000.00")), eq("VND"), anyString());
        // Hạch toán Fineract
        verify(fineractJournalService).recordSale(anyString(), eq(new BigDecimal("120000.00")), anyString(), any());
        // Rank được nâng lên DIAMOND
        assertThat(user.getRank()).isEqualTo("DIAMOND");
    }

    @Test
    @DisplayName("Gói Ma Thuật (DIAMOND): Luôn FREE vận chuyển 100% và tự động chiết khấu 3% toàn đơn hàng")
    void testMaThuatPricingAlwaysFreeShippingAnd3PercentDiscount() {
        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Collections.emptyMap());

        Attributes attr = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-PRO").build())
                .price(500000.0)
                .salePrice(500000.0)
                .build();

        CreateOrderRequest.OrderItemRequest item = CreateOrderRequest.OrderItemRequest.builder()
                .attributesSku("SKU-PRO")
                .quantity(2)
                .build();

        OrderDiscountContext context = OrderDiscountContext.builder()
                .customerId("200")
                .userRank(UserRank.DIAMOND) // Gói Ma Thuật
                .shippingMethod(ShippingMethod.DELIVERY)
                .rawShippingFee(50000.0) // 50.000 đ phí ship
                .items(List.of(item))
                .attributesList(List.of(attr))
                .discountCodes(Collections.emptyList())
                .build();

        DiscountEvaluationResult result = discountProcessor.evaluateDiscount(context);

        // 1. Phí ship giảm 100% (50.000 đ)
        assertThat(result.getShippingDiscountAmount()).isEqualTo(50000.0);
        assertThat(result.getAppliedDiscountCodes()).contains("MA_THUAT_FREE_SHIPPING");

        // 2. Chiết khấu 3% trên 1.000.000 đ = 30.000 đ
        assertThat(result.getGlobalDiscountAmount()).isEqualTo(30000.0);
        assertThat(result.getAppliedDiscountCodes()).contains("MA_THUAT_3%_DISCOUNT");
    }

    @Test
    @DisplayName("Gói Ánh Kim (SILVER): Không chiết khấu đơn hàng (0%) và phí ship tính bình thường")
    void testAnhKimPricingNoDirectDiscountAndNormalShipping() {
        when(voucherRedisService.getValidVouchers(anyList())).thenReturn(Collections.emptyMap());

        Attributes attr = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-STD").build())
                .price(500000.0)
                .salePrice(500000.0)
                .build();

        CreateOrderRequest.OrderItemRequest item = CreateOrderRequest.OrderItemRequest.builder()
                .attributesSku("SKU-STD")
                .quantity(1)
                .build();

        OrderDiscountContext context = OrderDiscountContext.builder()
                .customerId("100")
                .userRank(UserRank.SILVER) // Gói Ánh Kim
                .shippingMethod(ShippingMethod.DELIVERY)
                .rawShippingFee(40000.0)
                .items(List.of(item))
                .attributesList(List.of(attr))
                .discountCodes(Collections.emptyList())
                .build();

        DiscountEvaluationResult result = discountProcessor.evaluateDiscount(context);

        // Không có chiết khấu trực tiếp (0%)
        assertThat(result.getGlobalDiscountAmount()).isEqualTo(0.0);
        // Không free ship tự động
        assertThat(result.getShippingDiscountAmount()).isEqualTo(0.0);
    }
}
