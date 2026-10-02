package com.ddicg.erp.modules.payment.service.client;

import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;

import java.math.BigDecimal;

public interface PaymentGatewayClient {
    CustomerPaymentProfile vaultPaymentMethod(Long userId, String googlePayToken, String cardNetwork, String lastFour);
    MitChargeResult chargeRecurringMit(String multiUseToken, BigDecimal amount, String currency, String referenceId);

    record MitChargeResult(boolean success, String transactionId, String errorCode, String errorMessage) {}
}
