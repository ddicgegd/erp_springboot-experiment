package com.ddicg.erp.modules.subscription.dto.response;

import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSubscriptionResponse {
    Long id;
    Long userId;
    Long planId;
    String planCode;
    String planName;
    String targetRank;
    Integer billingPeriodMonths;
    SubscriptionState subscriptionState;
    Boolean isTrial;
    LocalDateTime startTime;
    LocalDateTime expiryTime;
    Boolean autoRenewing;
    String paymentProfileId;
}
