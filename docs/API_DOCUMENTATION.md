# ERP Spring Boot Experiment - Toàn bộ Đặc tả REST API

Tài liệu được tự động đồng bộ bởi **`api-documentation-generator`** kết hợp **`codebase-onboarding`**.
* **Dự án**: `erp_springboot-experiment`
* **Phiên bản**: `1.0.0`
* **Base URL**: `http://localhost:8080`
* **Authentication**: Bearer JWT Token (`Authorization: Bearer <token>`)

---

## Mục lục Modules API
1. [Module IAM (Xác thực & Người dùng)](#1-module-iam-authentication--user)
2. [Module Merchandise (Hàng hóa, Danh mục & Thuộc tính)](#2-module-merchandise-catalog--inventory)
3. [Module Cart (Giỏ hàng Redis)](#3-module-cart-shopping-cart)
4. [Module Order (Đơn hàng & Giao hàng)](#4-module-order-order-lifecycle--checkout)
5. [Module Fineract (Core Banking, Sổ cái & Khách hàng)](#5-module-fineract-banking--ledger-gateway)

---

## 1. Module IAM (Authentication & User)
*Base Path*: `/api/auth`

### 1.1. Đăng nhập (`POST /api/auth/login`)
* **Mô tả**: Xác thực người dùng bằng email và mật khẩu, trả về Access Token và Refresh Token.
* **Headers**: `Content-Type: application/json`
* **Request Body**:
```json
{
  "email": "user@example.com",
  "password": "Password123@"
}
```
* **Response 200 OK**:
```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5c...",
    "refreshToken": "7c9e6679-7425-40de-944b-...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "email": "user@example.com",
      "username": "user123",
      "roles": ["ROLE_CUSTOMER"]
    }
  }
}
```

### 1.2. Đăng ký tài khoản (`POST /api/auth/register`)
* **Request Body**:
```json
{
  "email": "newuser@example.com",
  "password": "Password123@",
  "fullName": "Nguyen Van A",
  "phone": "0987654321"
}
```

### 1.3. Lấy thông tin cá nhân (`GET /api/auth/me`)
* **Auth**: Yêu cầu `Bearer <token>`
* **Response 200 OK**: Thông tin chi tiết hồ sơ `MyProfileResponse`.

---

## 2. Module Merchandise (Catalog & Inventory)
*Base Path*: `/api/merchandise`

### 2.1. Thêm sản phẩm mới (`POST /api/merchandise/add-Product`)
* **Auth**: Yêu cầu Role `ADMIN` hoặc `STAFF`
* **Request Body**:
```json
{
  "name": "Áo Thun Cotton Cao Cấp",
  "description": "Chất liệu 100% cotton thoáng mát",
  "categoryId": 5,
  "price": 250000,
  "sku": "TSHIRT-COTTON-01",
  "stockQuantity": 100
}
```

### 2.2. Tìm kiếm & Phân trang sản phẩm (`POST /api/merchandise/search-Product`)
* **Request Body**:
```json
{
  "keyword": "Áo thun",
  "categoryId": 5,
  "page": 0,
  "size": 20,
  "sortBy": "price",
  "sortDirection": "ASC"
}
```

### 2.3. Quản lý Danh mục & Thuộc tính
* `POST /api/merchandise/add-Category`: Tạo danh mục hàng hóa mới.
* `POST /api/merchandise/search-Category`: Tìm kiếm danh mục.
* `POST /api/merchandise/add-Attributes`: Thêm thuộc tính biến thể (Màu sắc, Kích cỡ).
* `POST /api/merchandise/add-Product-Images/{sku}`: Upload ảnh sản phẩm lên MinIO.

---

## 3. Module Cart (Shopping Cart)
*Base Path*: `/api/v1/cart`

### 3.1. Lấy thông tin giỏ hàng (`GET /api/v1/cart`)
* **Auth**: Yêu cầu đăng nhập hoặc Guest Cart Token.
* **Storage**: Dữ liệu lưu trữ trên **Redis**.

### 3.2. Thêm sản phẩm vào giỏ (`POST /api/v1/cart/items`)
* **Request Body**:
```json
{
  "sku": "TSHIRT-COTTON-01",
  "quantity": 2
}
```

---

## 4. Module Order (Order Lifecycle & Checkout)
*Base Path*: `/api/orders`

### 4.1. Tạo đơn hàng mới (`POST /api/orders`)
* **Auth**: Yêu cầu `Bearer <token>`
* **Luồng xử lý**: Kiểm tra tồn kho $\to$ Lưu Order vào Oracle DB $\to$ Bắn Domain Event sang Kafka `order-placed-topic`.
* **Request Body**:
```json
{
  "shippingAddress": "123 Nguyen Trai, Q1, TP.HCM",
  "receiverName": "Nguyen Van A",
  "receiverPhone": "0987654321",
  "paymentMethod": "VNPAY",
  "items": [
    {
      "sku": "TSHIRT-COTTON-01",
      "quantity": 2,
      "unitPrice": 250000
    }
  ]
}
```

### 4.2. Chuyển trạng thái giao hàng (`POST /api/orders/ship`)
* **Auth**: Role `ADMIN`
* **Mô tả**: Chuyển trạng thái sang `SHIPPED`, sinh mã PIN và Delivery Token cho shipper lưu vào Redis.

---

## 5. Module Fineract (Banking & Ledger Gateway)
*Base Path*: `/api/v1/erp`

### 5.1. Hạch toán doanh thu bán hàng (`POST /api/v1/erp/accounting/sales`)
* **Request Body**:
```json
{
  "orderId": "ORD-20260816-001",
  "amount": 500000.00,
  "note": "Hạch toán doanh thu bán hàng đơn ORD-20260816-001"
}
```
* **Xử lý**: Tự động tạo Journal Entries ghi Nợ/Có trên Apache Fineract Ledger.

### 5.2. Hạch toán hoàn tiền (`POST /api/v1/erp/accounting/refunds`)
* **Request Body**:
```json
{
  "orderId": "ORD-20260816-001",
  "amount": 500000.00,
  "note": "Hoàn tiền đơn hàng hủy"
}
```

### 5.3. Đồng bộ Khách hàng sang Core Banking (`POST /api/v1/erp/clients/sync`)
* **Mô tả**: Tự động kiểm tra hoặc tạo Client ID tương ứng của User trên hệ thống Apache Fineract.
