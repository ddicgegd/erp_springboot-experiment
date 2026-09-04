package com.ddicg.erp.modules.fineract.service.accounting;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.modules.fineract.config.FineractProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Phân giải tài khoản sổ cái kế toán (GL Accounts) theo phương thức thanh toán và nghiệp vụ ERP.
 */
@Component
@RequiredArgsConstructor
public class GlAccountResolver {

    private final FineractProperties fineractProperties;

    /**
     * Xác định tài khoản tài sản nhận tiền (Debit Cash hoặc Debit Bank)
     */
    public Long resolveAssetGlAccount(PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return fineractProperties.getCashGlAccountId();
        }

        return switch (paymentMethod) {
            case VNPAY, BANK_TRANSFER, CREDIT_CARD, MOMO, PAYPAL ->
                    fineractProperties.getBankGlAccountId() != null
                            ? fineractProperties.getBankGlAccountId()
                            : fineractProperties.getCashGlAccountId();
            case COD ->
                    fineractProperties.getCashGlAccountId();
        };
    }

    public Long getSalesRevenueGlAccountId() {
        return fineractProperties.getSalesRevenueGlAccountId();
    }

    public Long getSalesReturnsGlAccountId() {
        return fineractProperties.getSalesReturnsGlAccountId();
    }
}
