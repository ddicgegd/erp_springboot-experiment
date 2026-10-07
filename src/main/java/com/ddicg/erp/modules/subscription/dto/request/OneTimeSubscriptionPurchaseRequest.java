package com.ddicg.erp.modules.subscription.dto.request;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request thanh toán mua gói hội viên 1 lần (VNPay/Thẻ/Ngân hàng) - Trả trước và cộng dồn ngày khuyến mại")
public class OneTimeSubscriptionPurchaseRequest {

    @NotBlank(message = "Mã gói dịch vụ (planCode) không được để trống")
    @Schema(description = "Mã gói cước (ANH_KIM hoặc MA_THUAT)", example = "ANH_KIM")
    String planCode;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    @Schema(description = "Phương thức thanh toán (VNPAY, CREDIT_CARD, BANK_TRANSFER...)", example = "VNPAY")
    PaymentMethod paymentMethod;

    @Schema(description = "Mã tham chiếu giao dịch từ cổng thanh toán", example = "VNPAY_TXN_987654")
    String transactionReference;

    @Schema(description = "Ghi chú đơn hàng", example = "Thanh toán gói Ánh Kim qua VNPay QR")
    String note;
}
