package com.ddicg.erp.modules.subscription.service.client;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

public interface GooglePlaySubscriptionClient {

    @Data
    @Builder
    class SubscriptionPurchaseV2 {
        String subscriptionState; // SUBSCRIPTION_STATE_ACTIVE, etc.
        Instant startTime;
        Instant expiryTime;
        Boolean acknowledgementState;
        List<LineItem> lineItems;
    }

    @Data
    @Builder
    class LineItem {
        String productId;
        String offerId;
        String basePlanId;
        Instant expiryTime;
        Boolean autoRenewingPlan;
    }

    SubscriptionPurchaseV2 getSubscriptionPurchase(String token);

    void acknowledgeSubscription(String token);
}
