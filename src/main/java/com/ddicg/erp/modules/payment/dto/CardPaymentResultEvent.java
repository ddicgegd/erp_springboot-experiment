package com.ddicg.erp.modules.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event DTO nhận kết quả thanh toán bằng thẻ từ cổng thanh toán (VNPAY / PAYPAL) qua Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardPaymentResultEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Loại sự kiện (ví dụ: PAYMENT_CARD_COMPLETED)
     */
    private String eventType;

    /**
     * Cổng thanh toán (VNPAY / PAYPAL)
     */
    private String gateway;

    /**
     * Mã số đơn hàng
     */
    private String orderNumber;

    /**
     * Mã giao dịch từ cổng thanh toán (Transaction ID duy nhất)
     */
    private String transactionId;

    /**
     * Loại thẻ (VISA, MASTERCARD, JCB, v.v.)
     */
    private String cardType;

    /**
     * 4 số cuối của thẻ
     */
    private String cardLastFour;

    /**
     * Tổng số tiền thanh toán (Gross Amount)
     */
    private BigDecimal grossAmount;

    /**
     * Phí giao dịch cổng thanh toán (Fee Amount)
     */
    private BigDecimal feeAmount;

    /**
     * Số tiền thực nhận sau trừ phí (Net Amount)
     */
    private BigDecimal netAmount;

    /**
     * Đơn vị tiền tệ (VND, USD, v.v.)
     */
    private String currency;

    /**
     * Thời điểm thanh toán thành công
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paidAt;

    /**
     * Mã định danh gói thuê bao (nếu thanh toán cho subscription)
     */
    private Long subscriptionId;

    /**
     * Cờ đánh dấu giao dịch thanh toán cho Subscription (true) hay Order thường (false)
     */
    @JsonProperty("isSubscription")
    private Boolean isSubscription;

    public Boolean isSubscription() {
        return Boolean.TRUE.equals(this.isSubscription);
    }
}
