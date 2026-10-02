package com.ddicg.erp.modules.subscription.dto.response;

import com.ddicg.erp.modules.subscription.model.enums.SubscriptionState;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSubscriptionResponse {
    Long id;
    Long userId;
    String planCode;
    String targetRank;
    Integer billingPeriodMonths;
    SubscriptionState subscriptionState;
    Boolean isTrial;
    LocalDateTime startTime;
    LocalDateTime expiryTime;
    Boolean autoRenewing;
    String orderId;
}
