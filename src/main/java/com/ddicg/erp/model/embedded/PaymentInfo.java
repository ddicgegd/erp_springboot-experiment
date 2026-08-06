package com.ddicg.erp.model.embedded;

import com.ddicg.erp.model.enums.PaymentMethod;
import com.ddicg.erp.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Embeddable
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentInfo {

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    PaymentStatus paymentStatus;

    @Column(name = "payment_date")
    LocalDateTime paymentDate;

    @Column(name = "transaction_id", length = 200)
    String transactionId;

    @Column(name = "payment_gateway", length = 100)
    String paymentGateway; // VNPay, MoMo, etc.

    @Column(name = "paid_amount")
    Double paidAmount;

    @Column(name = "refund_amount")
    Double refundAmount;

    @Column(name = "refund_date")
    LocalDateTime refundDate;

    @Column(name = "payment_notes", length = 1000)
    String notes;
}
