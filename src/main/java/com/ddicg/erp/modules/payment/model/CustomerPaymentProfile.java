package com.ddicg.erp.modules.payment.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "customer_payment_profiles")
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerPaymentProfile extends BaseEntity<Long> {

    @Column(name = "profile_code", length = 64, nullable = false, unique = true)
    String profileCode;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @Column(name = "gateway", length = 50, nullable = false)
    String gateway;

    @Column(name = "multi_use_token", length = 255, nullable = false)
    String multiUseToken;

    @Column(name = "fpan_last_four", length = 4)
    String fpanLastFour;

    @Column(name = "card_network", length = 50)
    String cardNetwork;

    @Column(name = "status", length = 20, nullable = false)
    String status;

    @Column(name = "initial_cit_transaction_id", length = 100)
    String initialCitTransactionId;
}
