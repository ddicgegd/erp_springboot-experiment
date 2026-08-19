package com.ddicg.erp.modules.order.service.discount;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DiscountEvaluationResult implements Serializable {
    @Builder.Default
    Double productDiscountAmount = 0.0;

    @Builder.Default
    Double shippingDiscountAmount = 0.0;

    @Builder.Default
    List<String> appliedDiscountCodes = new java.util.ArrayList<>();

    @Builder.Default
    java.util.Map<String, Double> itemDiscounts = new java.util.HashMap<>();

    @Builder.Default
    java.util.Map<String, Double> itemDiscountPercentages = new java.util.HashMap<>();

    String description;
}
