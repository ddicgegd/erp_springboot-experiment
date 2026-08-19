package com.ddicg.erp.core.common.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShippingCalculationResult implements Serializable {
    Double shippingFee;
    Double distanceKm;
    String originName;
    String destinationName;
    String nearestHubName;
    String zoneTier;
    String estimatedDeliveryTime;
}
