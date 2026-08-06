package com.ddicg.erp.service.dto;

import com.ddicg.erp.model.entity.Payment;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for {@link Payment}
 */
@Value
public class PaymentDto implements Serializable {
    UUID id;
    String name;
    OrderDto order;
    String provider;
    String transactionCode;
    Double amount;
    String status;
    LocalDateTime paymentDate;
    String description;
    String rawResponse;
    String bankCode;
    String cardType;
    String bankTranNo;
    String ipAddress;
}