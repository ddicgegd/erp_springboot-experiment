# ERP Spring Boot Experiment - Toàn Bộ Đặc Tả Kiến Trúc & REST API Hợp Nhất

Tài liệu được cập nhật tự động bởi **`api-documentation-generator`** kết hợp **`improve-codebase-architecture`**.
* **Dự án**: `ERP_SpringBoot-Experiment`
* **Phiên bản**: `1.2.0 (August 2026 Release)`
* **Base URL**: `http://localhost:8080`
* **Xác thực**: JWT Bearer Token (`Authorization: Bearer <TOKEN>`)

---

## 1. Quy Chuẩn Kiến Trúc & API Envelopes

### 1.1. Cấu Trúc Phản Hồi Chuẩn (Standard Response Wrapper)
Mọi response trả về từ hệ thống đều được bọc trong đối tượng chuẩn `Response<T>`:

```json
{
  "status": {
    "message": "Thành công",
    "code": 200
  },
  "data": { ... }
}
```

Đối với danh sách phân trang `Response<PagingResponse<T>>`:
```json
{
  "status": {
    "message": "Thành công",
    "code": 200
  },
  "data": {
    "content": [ ... ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 75,
    "totalPages": 8,
    "last": false
  }
}
```

---

## 2. Module 1: IAM, Xác Thực & Địa Chỉ Phân Cấp (`/api/auth`, `/api/address`)

### 2.1. Đăng nhập hệ thống (`POST /api/auth/login`)
* **Quyền hạn**: `Public`
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
  "status": { "message": "Login successful", "code": 200 },
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "7c9e6679-7425-40de-944b-...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": 1,
      "email": "user@example.com",
      "username": "user123",
      "roles": ["ROLE_USER"]
    }
  }
}
```

### 2.2. Danh sách Endpoints Auth khác:
* `POST /api/auth/register`: Đăng ký tài khoản mới.
* `GET /api/auth/verify-email?token=...`: Kích hoạt tài khoản qua email.
* `POST /api/auth/refresh-token`: Cấp lại Access Token mới.
* `GET /api/auth/me`: Lấy thông tin tài khoản hiện tại (`Authenticated`).
* `PUT /api/auth/me`: Cập nhật thông tin tài khoản.
* `POST /api/auth/me/avatar`: Upload ảnh đại diện (`multipart/form-data`).

### 2.3. Quản lý Địa chỉ Giao hàng Phân cấp (`/api/address`)
* `GET /api/address`: Lấy danh sách địa chỉ đã lưu của tài khoản.
* `POST /api/address`: Thêm mới địa chỉ phân cấp (Tỉnh/Thành phố $\rightarrow$ Quận/Huyện $\rightarrow$ Phường/Xã $\rightarrow$ Số nhà/Thôn xóm).
* `PUT /api/address/{id}`: Cập nhật địa chỉ.
* `PUT /api/address/{id}/default`: Đặt làm địa chỉ mặc định.
* `DELETE /api/address/{id}`: Xóa địa chỉ.

---

## 3. Module 2: Quản Lý Hàng Hóa & Biến Thể Attributes (`/api/merchandise`)

Hỗ trợ đầy đủ việc quản lý sản phẩm đa biến thể (Attributes SKU) và hình ảnh:

### 3.1. Tìm kiếm & Phân trang Biến thể Attributes (`POST /api/merchandise/search-Attributes`)
* **Quyền hạn**: `Public`
* **Request Body**:
```json
{
  "page": 1,
  "size": 10
}
```
* **Response 200 OK**:
```json
{
  "status": { "message": "Success", "code": 200 },
  "data": {
    "content": [
      {
        "id": 847,
        "name": "Sony WH-1000XM6 Đen",
        "sku": { "sku": "ATTR-SWHXM6-BLACK" },
        "price": 9000000.0,
        "salePrice": 8100000.0,
        "variantOptions": [
          { "name": "Màu sắc", "values": ["Đen"] }
        ],
        "promotions": [
          {
            "name": "Sony ANC Week",
            "discountPercent": 10.0,
            "startDate": "2026-07-12T00:00:00",
            "endDate": "2026-07-22T00:00:00"
          }
        ]
      }
    ],
    "pageable": { "pageNumber": 0, "pageSize": 10, "totalElements": 75, "totalPages": 8 }
  }
}
```

### 3.2. Quản lý Sản phẩm & Danh mục:
* `POST /api/merchandise/add-Product`: Tạo sản phẩm mới (`Role: ADMIN/STAFF`).
* `PUT /api/merchandise/update-Product`: Sửa thông tin sản phẩm.
* `POST /api/merchandise/delete-Product`: Xóa sản phẩm theo danh sách SKU.
* `POST /api/merchandise/search-Product`: Tìm kiếm sản phẩm phân trang.
* `POST /api/merchandise/add-Category`: Tạo danh mục mới.
* `POST /api/merchandise/add-Attributes`: Thêm biến thể cho sản phẩm.

---

## 4. Module 3: Giỏ Hàng Trực Tuyến (`/api/cart`)

Tích hợp tự động với Redis Cache và tính năng tự động dọn dẹp SKU rác / hết hàng:

* `GET /api/cart`: Lấy thông tin giỏ hàng hiện tại của khách hàng.
* `POST /api/cart/add`: Thêm sản phẩm vào giỏ hàng (`CartItemRequest` với `attributesSku`, `quantity`).
* `PUT /api/cart/items`: Cập nhật số lượng sản phẩm trong giỏ hàng.
* `DELETE /api/cart/remove`: Xóa một hoặc nhiều SKU khỏi giỏ.
* `DELETE /api/cart/clear`: Xóa toàn bộ giỏ hàng.

---

## 5. Module 4: Phân Hệ Voucher & Giảm Giá Bóc Tách (`/api/vouchers`)

Quản lý khuyến mãi với cơ chế **Redis Natural TTL (`endDate - now`)** và **Bóc tách giảm giá theo SKU biến thể (`applicableSkus`)**.

### 5.1. Khách hàng xem Voucher khả dụng (`GET /api/vouchers/active`)
* **Quyền hạn**: `Public`
* **Response 200 OK**:
```json
{
  "status": { "message": "Lấy danh sách voucher khả dụng thành công", "code": 200 },
  "data": [
    {
      "id": 1,
      "code": "FREESHIP_MAX",
      "name": "Miễn Phí Vận Chuyển Siêu Cấp 50K",
      "voucherType": "SHIPPING",
      "discountType": "FIXED_AMOUNT",
      "discountValue": 50000.0,
      "minOrderAmount": 150000.0,
      "applicableSkus": null,
      "isValid": true
    },
    {
      "id": 52,
      "code": "SONY_ANC_500K",
      "name": "Giảm 500K Cho Tai Nghe Sony WH-1000XM6",
      "voucherType": "PRODUCT",
      "discountType": "FIXED_AMOUNT",
      "discountValue": 500000.0,
      "applicableSkus": ["ATTR-SWHXM6-BLACK", "ATTR-SWHXM6-BLUE"],
      "isValid": true
    }
  ]
}
```

### 5.2. Khách hàng kiểm tra nhanh mã voucher (`GET /api/vouchers/check/{code}`)
* **Quyền hạn**: `Public` (Truy vấn Redis RAM `< 0.2ms`)
* **Response 200 OK**:
```json
{
  "status": { "message": "Mã voucher hợp lệ", "code": 200 },
  "data": {
    "code": "SONY_ANC_500K",
    "voucherType": "PRODUCT",
    "discountType": "FIXED_AMOUNT",
    "discountValue": 500000.0,
    "minOrderAmount": 0.0,
    "applicableSkus": ["ATTR-SWHXM6-BLACK", "ATTR-SWHXM6-BLUE"],
    "isValid": true
  }
}
```

### 5.3. Admin tạo mới Voucher (`POST /api/vouchers`)
* **Quyền hạn**: `Role: ADMIN`
* **Request Body**:
```json
{
  "code": "SONY_ANC_500K",
  "name": "Giảm 500K Cho Tai Nghe Sony WH-1000XM6",
  "description": "Ưu đãi độc quyền cho 2 phiên bản màu Đen và Midnight Blue",
  "voucherType": "PRODUCT",
  "discountType": "FIXED_AMOUNT",
  "discountValue": 500000.0,
  "applicableSkus": ["ATTR-SWHXM6-BLACK", "ATTR-SWHXM6-BLUE"],
  "startDate": "2026-08-01T00:00:00",
  "endDate": "2026-12-31T23:59:59",
  "totalQuantity": 100
}
```

### 5.4. Admin cập nhật / xóa Voucher:
* `PUT /api/vouchers/{id}`: Cập nhật thông tin voucher (tự động đồng bộ lại Redis Cache).
* `DELETE /api/vouchers/{id}`: Xóa mềm voucher (xóa ngay khỏi Redis Cache).
* `GET /api/vouchers`: Phân trang danh sách voucher.
* `GET /api/vouchers/{id}`: Xem chi tiết voucher theo ID.

---

## 6. Module 5: Đơn Hàng, Tính Phí Giao Vận & Vòng Đời Trạng Thái (`/api/orders`, `/api/shipping`)

### 6.1. Tính cước vận chuyển chuẩn từ Kho Tổng Định Hòa (`POST /api/shipping/calculate`)
* **Quyền hạn**: `Public / Authenticated`
* **Request Body**:
```json
{
  "shippingAddress": "Số 12 ngõ nghè, Xã Định Hòa, Huyện Yên Định, Tỉnh Thanh Hóa",
  "items": [
    { "attributesSku": "ATTR-SWHXM6-BLACK", "quantity": 1 }
  ]
}
```
* **Response 200 OK**:
```json
{
  "status": { "message": "Tính phí vận chuyển thành công", "code": 200 },
  "data": {
    "shippingFee": 0.0,
    "distanceKm": 0.0,
    "zone": "LOCAL_SAME_WARD",
    "origin": "Xã Định Hòa, Huyện Yên Định, Thanh Hóa",
    "destination": "Xã Định Hòa, Yên Định, Thanh Hóa"
  }
}
```

### 6.2. Tạo Đơn Hàng Mới (`POST /api/orders`)
Hệ thống tự động bóc tách giảm giá từng món và cước vận chuyển:
* **Request Body**:
```json
{
  "shippingAddress": "Số 1 Hoàng Diệu, Phường Điện Biên, Quận Ba Đình, Hà Nội",
  "paymentMethod": "COD",
  "discountCodes": ["FREESHIP_MAX", "SONY_ANC_500K"],
  "items": [
    { "attributesSku": "ATTR-SWHXM6-BLACK", "quantity": 1 },
    { "attributesSku": "ATTR-AIRPODMAX-BLUE", "quantity": 1 }
  ]
}
```
* **Cơ chế tính toán**:
  - `ATTR-SWHXM6-BLACK` (9tr): Giảm 500k từ `SONY_ANC_500K` $\rightarrow$ Còn **8.500.000đ**.
  - `ATTR-AIRPODMAX-BLUE` (15tr): Không khớp mã $\rightarrow$ Giữ nguyên **15.000.000đ**.
  - Tiền ship (35k): Giảm 35k từ `FREESHIP_MAX` $\rightarrow$ **0đ**.
  - **Tổng thanh toán**: $\mathbf{23.500.000đ}$.

### 6.3. Vòng đời Trạng thái Đơn hàng & Quản trị Giao nhận:
* `POST /api/orders/confirm`: Xác nhận đơn hàng.
* `POST /api/orders/ship`: Bàn giao cho tài xế giao hàng (Tự động sinh mã **PIN Delivery** lưu vào Redis).
* `GET /api/orders/delivery-pin/{orderNumber}`: Admin xem mã PIN giao hàng.
* `POST /api/orders/complete`: Xác nhận giao thành công qua mã PIN.
* `POST /api/orders/cancel`: Hủy đơn khi ở trạng thái `PENDING`.
* `POST /api/orders/search`: Tìm kiếm & lọc đơn hàng quản trị.

---

## 7. Module 6: Tích Hợp Core Banking Apache Fineract (`/api/v1/erp/*`)

Tích hợp tài chính ngân hàng, quản lý khoản vay và tự động hạch toán sổ cái:
* `GET /api/v1/erp/loans`: Danh sách toàn bộ khoản vay.
* `GET /api/v1/erp/loans/my`: Danh sách khoản vay của tài khoản hiện tại.
* `POST /api/v1/erp/loans/my`: Nộp hồ sơ đăng ký khoản vay mới.
* `POST /api/v1/erp/loans/{loanId}/repayments`: Thanh toán trả nợ khoản vay.
* `GET /api/v1/erp/loan-products`: Danh mục các gói tín dụng ngân hàng.
* `GET /api/v1/erp/journal-entries`: Truy vấn bút toán sổ cái kế toán tự động.
* `GET /api/v1/erp/clients`: Quản lý hồ sơ khách hàng tài chính.
