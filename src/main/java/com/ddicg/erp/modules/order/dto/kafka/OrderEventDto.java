package com.ddicg.erp.modules.order.dto.kafka;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderEventDto {
    /**
     * Mã cổng thanh toán (VD: "VNPAY", "MOMO", "ZALOPAY", "PAYPAL")
     */
    String paymentProvider;

    double amount;

    /**
     * Đơn vị tiền tệ (VD: "VND", "USD")
     */
    String currency;

    /**
     * Mã đơn hàng nội bộ của bạn (vnp_TxnRef, app_trans_id, v.v.)
     */
    String orderId;

    /**
     * Nội dung/Mô tả thanh toán
     */
    String orderDescription;


    CustomerInfo customerInfo;

    PaymentOptions paymentOptions;


    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
