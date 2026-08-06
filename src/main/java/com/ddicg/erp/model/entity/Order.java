package com.ddicg.erp.model.entity;

import com.ddicg.erp.config.converter.OrderStatusListConverter;
import com.ddicg.erp.model.base.IdentityOnly;
import com.ddicg.erp.model.embedded.AuditInfo;
import com.ddicg.erp.model.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_customer_id", columnList = "customer_id"),
    @Index(name = "idx_tracking_number", columnList = "tracking_number")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order extends IdentityOnly<Long> {

  /*
   * ============================ 🔢 Order Information
   * ============================
   */

  /**
   * Số đơn hàng
   * 
   * @en Order number
   */
  @Column(name = "order_number", unique = true, nullable = false, length = 50)
  String orderNumber;

  /**
   * Lịch sử trạng thái đơn hàng, chỉ được append, không được xóa.
   * DB lưu dạng JSON: ["PENDING","CONFIRMED","COMPLETED"]
   * Trạng thái hiện tại là phần tử cuối cùng.
   *
   * @en Order status history, append-only.
   *     Stored as JSON: ["PENDING","CONFIRMED","COMPLETED"]
   *     Current status is the last element.
   */
  @Convert(converter = OrderStatusListConverter.class)
  @Column(name = "order_status", nullable = false, columnDefinition = "CLOB")
  @Builder.Default
  List<OrderStatus> status = new ArrayList<>();

  /**
   * Trạng thái hiện tại (để query tối ưu)
   * 
   * @en Current status (for optimized querying)
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", length = 50)
  OrderStatus currentStatus;

  @Column(name = "tracking_number", length = 100)
  String trackingNumber;

  /*
   * ============================ 👤 Customer Information
   * ============================
   */

  /**
   * Khách hàng
   * 
   * @en Customer
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "FK_order_customer"))
  @ToString.Exclude
  User customer;

  /**
   * Tên khách hàng (Lưu tại thời điểm đặt hàng)
   * 
   * @en Customer name (Saved at the time of ordering)
   */
  @Column(name = "customer_name", nullable = false, length = 200)
  String customerName;

  /**
   * Email khách hàng
   * 
   * @en Customer email
   */
  @Column(name = "customer_email", length = 200)
  String customerEmail;

  /**
   * Số điện thoại khách hàng
   * 
   * @en Customer phone
   */
  @Column(name = "customer_phone", length = 20)
  String customerPhone;

  /* ============================ 📦 Order Items ============================ */

  /**
   * Danh sách sản phẩm trong đơn hàng
   * 
   * @en Order items list
   */
  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @Builder.Default
  List<OrderItem> orderItems = new ArrayList<>();

  /*
   * ============================ 💰 Pricing Information
   * ============================
   */

  /**
   * Tổng tiền hàng
   * 
   * @en Subtotal
   */
  @Column(name = "subtotal", nullable = false)
  @Builder.Default
  Double subtotal = 0.0;

  /**
   * Số tiền giảm giá
   * 
   * @en Discount amount
   */
  @Column(name = "discount_amount")
  @Builder.Default
  Double discountAmount = 0.0;

  /**
   * Mã giảm giá
   * 
   * @en Discount code
   */
  @Column(name = "discount_code", length = 100)
  String discountCode;

  /**
   * Số tiền thuế
   * 
   * @en Tax amount
   */
  @Column(name = "tax_amount")
  @Builder.Default
  Double taxAmount = 0.0;

  /**
   * Phí vận chuyển
   * 
   * @en Shipping fee
   */
  @Column(name = "shipping_fee")
  @Builder.Default
  Double shippingFee = 0.0;

  /**
   * Tổng số tiền
   * 
   * @en Total amount
   */
  @Column(name = "total_amount", nullable = false)
  @Builder.Default
  Double totalAmount = 0.0;

  /* ======================= 🚚 Shipping Information ======================= */

  /**
   * Thông tin vận chuyển
   * 
   * @en Shipping info
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shipping_address_id", foreignKey = @ForeignKey(name = "FK_order_shipping_address"))
  Address shippingInfo;

  /*
   * ============================ 💳 Payment Information
   * ============================
   */

  /**
   * Phung thức vận chuyển
   * 
   * @en Shipping method
   */
  @Column(name = "shipping_method", length = 100)
  String shippingMethod;

  /* ============================ 🚛 Delivery ============================ */

  /**
   * Ngày giao dự kiến
   * 
   * @en Estimated Delivery Date
   */
  @Column(name = "estimated_delivery_date")
  LocalDateTime estimatedDeliveryDate;

  /**
   * Ngày giao thực tế
   * 
   * @en Actual Delivery Date
   */
  @Column(name = "actual_delivery_date")
  LocalDateTime actualDeliveryDate;

  /*
   * ============================ 📝 Additional Information
   * ============================
   */

  /**
   * Ghi chú của khách hàng
   * 
   * @en Customer notes
   */
  @Column(name = "customer_notes", length = 2000)
  String customerNotes;

  /**
   * Ghi chú nội bộ
   * 
   * @en Admin notes
   */
  @Column(name = "admin_notes", length = 2000)
  String adminNotes;

  /**
   * Lý do hủy đơn
   * 
   * @en Cancellation reason
   */
  @Column(name = "cancellation_reason", length = 1000)
  String cancellationReason;

  /**
   * Thời gian hủy
   * 
   * @en Cancelled at
   */
  @Column(name = "cancelled_at")
  LocalDateTime cancelledAt;

  /**
   * Người hủy (User ID)
   * 
   * @en Cancelled by (User ID)
   */
  @Column(name = "cancelled_by")
  String cancelledBy;

  /**
   * Thời gian xác nhận
   * 
   * @en Confirmed at
   */
  @Column(name = "confirmed_at")
  LocalDateTime confirmedAt;

  /**
   * Người xác nhận (User ID)
   * 
   * @en Confirmed by (User ID)
   */
  @Column(name = "confirmed_by")
  String confirmedBy;

  /**
   * Thời gian hoàn thành
   * 
   * @en Completed at
   */
  @Column(name = "completed_at")
  LocalDateTime completedAt;

  /*
   * ============================ 🔗 Related Entities ============================
   */

  @Column(name = "shipper_id", length = 100)
  String shipperId;

  @Column(name = "shipper_name", length = 200)
  String shipperName;

  @Column(name = "shipper_phone", length = 20)
  String shipperPhone;

  @Column(name = "delivery_token", length = 255)
  String deliveryToken;

  /*
   * ============================ 🧩 Embedded Fields ============================
   */

  /**
   * Thông tin kiểm toán
   * 
   * @en Audit info
   */
  @Embedded
  @Builder.Default
  AuditInfo auditInfo = new AuditInfo();

}
