package com.ddicg.erp.modules.payment.service.client;

import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnMissingBean(name = "realPaymentGatewayClient")
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    public CustomerPaymentProfile vaultPaymentMethod(Long userId, String googlePayToken, String cardNetwork, String lastFour) {
        log.info("MockPaymentGatewayClient: Vaulting Google Pay token for user {}", userId);
        String profileId = "prof_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String multiUseToken = "vault_tok_" + UUID.randomUUID().toString().replace("-", "");
        String citTxnId = "cit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        return CustomerPaymentProfile.builder()
                .id(profileId)
                .userId(userId)
                .gateway("GOOGLE_PAY_INTERNAL")
                .multiUseToken(multiUseToken)
                .cardNetwork(cardNetwork != null ? cardNetwork : "VISA")
                .fpanLastFour(lastFour != null ? lastFour : "4242")
                .status("ACTIVE")
                .initialCitTransactionId(citTxnId)
                .build();
    }

    @Override
    public MitChargeResult chargeRecurringMit(String multiUseToken, BigDecimal amount, String currency, String referenceId) {
        log.info("MockPaymentGatewayClient: Charging recurring MIT for token {}, amount={} {}", multiUseToken, amount, currency);
        if ("vault_tok_fail".equals(multiUseToken)) {
            return new MitChargeResult(false, null, "INSUFFICIENT_FUNDS", "Thẻ không đủ số dư để thanh toán");
        }
        String txnId = "mit_txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new MitChargeResult(true, txnId, null, null);
    }
}
