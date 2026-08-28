package com.ddicg.erp.modules.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerInfo {
    @Column(name = "customer_id")
    Long customerId;

    @Column(name = "customer_name", length = 200)
    String customerName;

    @Column(name = "customer_email", length = 200)
    String customerEmail;

    @Column(name = "customer_phone", length = 20)
    String customerPhone;

    @Column(name = "shipping_address", length = 500)
    String shippingAddress;
}
