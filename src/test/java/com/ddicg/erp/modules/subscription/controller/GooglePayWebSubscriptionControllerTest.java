package com.ddicg.erp.modules.subscription.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.subscription.dto.request.GooglePayWebRegisterRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import com.ddicg.erp.modules.subscription.service.GooglePayWebService;
import com.ddicg.erp.modules.subscription.service.SubscriptionBillingService;
import com.ddicg.erp.modules.subscription.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GooglePayWebSubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private GooglePayWebService googlePayWebService;

    @Mock
    private SubscriptionBillingService billingService;

    @Mock
    private SecurityUtil securityUtil;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    @DisplayName("GET /api/v1/subscriptions/plans trả về danh sách gói")
    void testGetActivePlans() {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(1L)
                .planCode("GOLD_3M")
                .name("Gold 3M")
                .priceAmount(BigDecimal.valueOf(299000))
                .build();
        when(subscriptionService.getActivePlans()).thenReturn(List.of(plan));

        ResponseEntity<Response<List<SubscriptionPlan>>> res = subscriptionController.getActivePlans();
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getData().size());
        assertEquals("GOLD_3M", res.getBody().getData().get(0).getPlanCode());
    }

    @Test
    @DisplayName("POST /api/v1/subscriptions/google-pay/register đăng ký gói dùng thử 30 ngày")
    void testRegisterGooglePayWebSubscription() {
        when(securityUtil.getCurrentUserId()).thenReturn("123");

        GooglePayWebRegisterRequest req = GooglePayWebRegisterRequest.builder()
                .planCode("GOLD_6M")
                .paymentToken("token_web_test")
                .cardNetwork("VISA")
                .lastFour("4242")
                .build();

        UserSubscriptionResponse mockResp = UserSubscriptionResponse.builder()
                .id(101L)
                .userId(123L)
                .planCode("GOLD_6M")
                .subscriptionState(SubscriptionState.TRIALING)
                .isTrial(true)
                .targetRank("GOLD")
                .paymentProfileId("prof_123")
                .startTime(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusDays(30))
                .build();

        when(googlePayWebService.registerTrialSubscription(eq(123L), any(GooglePayWebRegisterRequest.class)))
                .thenReturn(mockResp);

        ResponseEntity<Response<UserSubscriptionResponse>> res = subscriptionController.registerGooglePayWebSubscription(req);

        assertNotNull(res.getBody());
        assertEquals(SubscriptionState.TRIALING, res.getBody().getData().getSubscriptionState());
        assertTrue(res.getBody().getData().getIsTrial());
        verify(googlePayWebService).registerTrialSubscription(eq(123L), any(GooglePayWebRegisterRequest.class));
    }
}
