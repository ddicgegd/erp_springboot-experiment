package com.ddicg.erp.modules.subscription.service;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GooglePayWebServiceTest {

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

    @InjectMocks
    private GooglePayWebService googlePayWebService;

    private User user;
    private SubscriptionPlan plan6M;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(10L)
                .email("webuser@example.com")
                .rank(UserRank.MEMBER)
                .build();

        plan6M = SubscriptionPlan.builder()
                .id(2L)
                .planCode("GOLD_6M")
                .name("Gói Gold 6 Tháng")
                .targetRank("GOLD")
                .billingPeriodMonths(6)
                .freeTrialDays(30)
                .priceAmount(BigDecimal.valueOf(550000))
                .currency("VND")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Should vault Google Pay token to User account and activate 30-day Free Trial")
    void testRegisterTrialSubscription() {
        String paymentTokenPayload = "{\"signature\":\"sig_xyz\",\"intermediateSigningKey\":{},\"protocolVersion\":\"ECv2\",\"signedMessage\":\"{\\\"encryptedMessage\\\":\\\"token_data_abc\\\"}\"}";

        GooglePayWebRegisterRequest request = GooglePayWebRegisterRequest.builder()
                .planCode("GOLD_6M")
                .paymentToken(paymentTokenPayload)
                .cardNetwork("VISA")
                .lastFour("4242")
                .build();

        CustomerPaymentProfile createdProfile = CustomerPaymentProfile.builder()
                .id(1L)
                .profileCode("prof_user10_001")
                .userId(10L)
                .gateway("GOOGLE_PAY_INTERNAL")
                .multiUseToken("vault_tok_test_123")
                .fpanLastFour("4242")
                .cardNetwork("VISA")
                .status("ACTIVE")
                .initialCitTransactionId("cit_txn_001")
                .build();

        when(planRepository.findByPlanCode("GOLD_6M")).thenReturn(Optional.of(plan6M));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(paymentGatewayClient.vaultPaymentMethod(eq(10L), anyString(), eq("VISA"), eq("4242")))
                .thenReturn(createdProfile);
        when(profileRepository.save(any(CustomerPaymentProfile.class))).thenReturn(createdProfile);
        when(userSubscriptionRepository.save(any(UserSubscription.class))).thenAnswer(i -> {
            UserSubscription s = i.getArgument(0);
            s.setId(501L);
            return s;
        });

        UserSubscriptionResponse response = googlePayWebService.registerTrialSubscription(10L, request);

        assertNotNull(response);
        assertEquals(SubscriptionState.TRIALING, response.getSubscriptionState());
        assertTrue(response.getIsTrial());
        assertEquals("GOLD", response.getTargetRank());
        assertEquals("prof_user10_001", response.getPaymentProfileId());

        verify(profileRepository).save(any(CustomerPaymentProfile.class));
        verify(userRepository).save(argThat(u -> "GOLD".equals(u.getRank())));
    }
}
