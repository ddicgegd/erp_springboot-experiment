package com.ddicg.erp.modules.order.dto.kafka;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOptions {
    String paymentMethod;     // Phương thức (VD: MoMo dùng requestType như "captureWallet", "payWithATM")
    String bankCode;          // Mã ngân hàng (VD: NCB, VNPAYQR cho VNPay)
    String extraData;         // Dữ liệu Passthrough gửi đi sao nhận lại vậy (VD: coupon_code, session_id)
}
