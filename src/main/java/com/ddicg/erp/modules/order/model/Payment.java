package com.ddicg.erp.modules.order.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment extends IdentityOnly<UUID> {

    /**
     * Đơn hàng liên kết
     * @en Linked order
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    Order order;

    /**
     * Nhà cung cấp dịch vụ thanh toán
     * @en Payment provider
     */
    @Column(name = "provider", length = 50)
    String provider;

    /**
     * Mã giao dịch
     * @en Transaction code
     */
    @Column(name = "transaction_code", length = 100)
    String transactionCode;

    /**
     * Số tiền
     * @en Amount
     */
    @Column(name = "amount")
    Double amount;

    /**
     * Trạng thái
     * @en Status
     */
    @Column(name = "status", length = 50)
    String status;

    /**
     * Ngày thanh toán
     * @en Payment date
     */
    @Column(name = "payment_date")
    LocalDateTime paymentDate;

    /**
     * Mô tả thanh toán
     * @en Payment description
     */
    @Column(name = "description", length = 500)
    String description;

    /**
     * Phản hồi gốc
     * @en Raw response
     */
    @Column(name = "raw_response", columnDefinition = "CLOB")
    String rawResponse;

    /**
     * Mã ngân hàng
     * @en Bank code
     */
    @Column(name = "bank_code", length = 50)
    String bankCode;

    /**
     * Loại thẻ
     * @en Card type
     */
    @Column(name = "card_type", length = 50)
    String cardType;

    /**
     * Số giao dịch ngân hàng
     * @en Bank transaction number
     */
    @Column(name = "bank_tran_no", length = 100)
    String bankTranNo;

    /**
     * Địa chỉ IP
     * @en IP address
     */
    @Column(name = "ip_address", length = 50)
    String ipAddress;

}
