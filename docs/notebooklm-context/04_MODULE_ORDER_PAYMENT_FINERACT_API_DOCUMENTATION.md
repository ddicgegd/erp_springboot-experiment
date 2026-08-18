# 04 - MODULE ORDER PAYMENT AND FINERACT API DOCUMENTATION

## 1. Overview & Scope
Phân hệ Đơn Hàng (Order), Giỏ Hàng (Cart), Cổng Thanh Toán VNPay và Kế Toán Ngân Hàng Apache Fineract phụ trách toàn bộ luồng thương mại từ lúc khách chọn hàng đến khi hoàn tất thanh toán và hạch toán sổ cái (General Ledger).

---

## 2. API Endpoints Specification

### 2.1 Shopping Cart Management (`/api/cart`)

- `GET /api/cart`: Lấy thông tin giỏ hàng hiện tại của khách hàng.
- `POST /api/cart/add`: Thêm danh sách biến thể SKU vào giỏ hàng.
```json
[
  {
    "attributeSku": "attr-92-4821",
    "quantity": 2
  }
]
```
- `DELETE /api/cart/remove`: Xóa danh sách sản phẩm khỏi giỏ hàng (`{"skus": ["attr-92-4821"]}`).
- `DELETE /api/cart/clear`: Xóa rỗng giỏ hàng.

---

### 2.2 Order Lifecycle Management (`/api/orders`)

#### Create Order (Checkout)
- **HTTP Method:** `POST`
- **Path:** `/api/orders`
- **Request Body (`CreateOrderRequest`):**
```json
{
  "shippingAddress": {
    "recipientName": "Nguyễn Văn A",
    "phoneNumber": "0987654321",
    "streetAddress": "123 Đường Lê Lợi",
    "ward": "Bến Nghé",
    "city": "Hồ Chí Minh"
  },
  "paymentMethod": "VNPAY", // "VNPAY", "CASH", "BANK_TRANSFER"
  "notes": "Giao hàng giờ hành chính"
}
```
- **Success Response (`201 Created`):** `Response<OrderDto>` với `orderNumber` (vd: `ORD-20260816-9921`) và URL chuyển hướng thanh toán VNPay nếu chọn VNPAY.

#### Order State Transitions & Delivery PIN
- `POST /api/orders/transition`: Chuyển trạng thái đơn hàng (`PROCESSING`, `DELIVERED`, `COMPLETED`, `CANCELLED`).
- `POST /api/orders/ship`: Bắt buộc điền thông tin tài xế. Tự động sinh mã `deliveryPin` và Delivery Token lưu vào Redis.
- `GET /api/orders/delivery-pin/{orderNumber}`: Admin tra cứu mã PIN của shipper để bàn giao.

---

### 2.3 VNPay Webhook & IPN Specification

#### Endpoint: `GET /api/orders/vnpay-ipn` & `POST /api/orders/vnpay-webhook`
- **Quy tắc Checksum SHA512:**
  - Sắp xếp toàn bộ query params bắt đầu bằng `vnp_` theo thứ tự alphabet.
  - Tạo chuỗi hash SHA512/HMAC-SHA512 với `vnp_HashSecret`.
  - So khớp với `vnp_SecureHash` gửi về.
- **Quy tắc Idempotency & Amount Verification:**
  - Nếu số tiền `vnp_Amount / 100 != order.totalAmount` $\rightarrow$ Trả về `{"RspCode":"04", "Message":"Invalid Amount"}`.
  - Nếu đơn hàng đã `PAID` trước đó $\rightarrow$ Trả về `{"RspCode":"02", "Message":"Order already confirmed"}`.
  - Nếu hợp lệ $\rightarrow$ Chuyển `Order.status = PAID`, `Payment.status = SUCCESS`, trả về `{"RspCode":"00", "Message":"Confirm Success"}`.

---

### 2.4 Apache Fineract Integration (`/api/v1/erp/...`)

#### Client & Account Sync:
- `POST /api/v1/erp/clients/sync`: Tự động đồng bộ profile User sang Fineract Client ID để mở tài khoản kế toán.
- `GET /api/v1/erp/clients`: Danh sách khách hàng đã đăng ký trên hệ thống Core Banking.

#### General Ledger Journal Entries:
- `POST /api/v1/erp/journals`: Ghi nhận bút toán kế toán tự động:
  - Khi Order thanh toán thành công: Ghi Nợ (Debit) TK Tiền gửi VNPay / Ghi Có (Credit) TK Doanh thu bán hàng.
  - Khi Order bị hoàn tiền: Ghi Nợ TK Giảm trừ doanh thu / Ghi Có TK Hoàn tiền khách hàng.
