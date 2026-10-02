package com.ddicg.erp.modules.subscription.service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnMissingBean(type = "com.google.api.services.androidpublisher.AndroidPublisher")
@Slf4j
public class MockGooglePlaySubscriptionClient implements GooglePlaySubscriptionClient {

    @Override
    public SubscriptionPurchaseV2 getSubscriptionPurchase(String token) {
        log.info("[MockGooglePlayClient] Verifying token {}", token);
        Instant now = Instant.now();
        return SubscriptionPurchaseV2.builder()
                .subscriptionState("SUBSCRIPTION_STATE_ACTIVE")
                .startTime(now)
                .expiryTime(now.plus(30, ChronoUnit.DAYS))
                .acknowledgementState(false)
                .lineItems(List.of(LineItem.builder()
                        .productId("sub_vip")
                        .basePlanId("plan-3-months")
                        .offerId("free-trial-1m")
                        .expiryTime(now.plus(30, ChronoUnit.DAYS))
                        .autoRenewingPlan(true)
                        .build()))
                .build();
    }

    @Override
    public void acknowledgeSubscription(String token) {
        log.info("[MockGooglePlayClient] Acknowledged token {}", token);
    }
}
