# API Documentation: Bookmark Service (Phụ kiện mua cùng tại PDP)

Tài liệu đặc tả toàn diện các API của **Bookmark Service** nhằm giải quyết bài toán: Người dùng đứng tại trang chi tiết sản phẩm (PDP) chọn mua nhiều phụ kiện kèm theo mà không đẩy vào giỏ hàng tổng, hỗ trợ lưu trữ tạm thời (1 giờ) và lưu chính thức (7 ngày) để khách quay lại thanh toán sau 5–10 tiếng.

---

## 1. Tổng quan & Xác thực (Overview & Authentication)

- **Base URL:** `http://localhost:8080/api/bookmarks`
- **Content-Type:** `application/json`
- **Cấu trúc Envelope chuẩn (`Response<T>`):**
  Tất cả các API thành công đều được bọc trong envelope chuẩn của hệ thống:
  ```json
  {
    "status": {
      "code": 200,
      "message": "Mô tả thành công"
    },
    "data": { ... }
  }
  ```
  *(Đối với các response kiểu `Response<Void>`, trường `data` là `null` hoặc tự động lược bỏ theo cấu hình JSON non-null).*

- **Cơ chế xác thực kép (Dual Identity Context):**
  - **User đã đăng nhập:** Gửi header `Authorization: Bearer <jwt_token>`. Hệ thống tự trích xuất `userId`.
  - **Khách vãng lai (Guest):** Gửi header `X-Guest-Id: <client_uuid>` (UUID cố định sinh bởi Client và lưu ở LocalStorage/Cookie).
  - *Bắt buộc phải có 1 trong 2 header trên, nếu thiếu cả hai hệ thống sẽ trả về lỗi `401 Unauthorized`.*

---

## 2. Bảng tổng hợp Endpoints

| Method   | Endpoint                               | Mô tả                                                         | TTL Redis (Fixed Window - Không Reset) |
| -------- | -------------------------------------- | ------------------------------------------------------------- | -------------------------------------- |
| `POST`   | `/api/bookmarks/{mainSku}/items`       | Thêm/cộng dồn 1 phụ kiện vào Bookmark (khi bấm `+`)           | **7 ngày** (Không reset khi add thêm)  |
| `POST`   | `/api/bookmarks/{mainSku}/staging`     | Tự động lưu tạm danh sách phụ kiện (auto-save ngầm)           | **1 giờ** (Không reset khi sync thêm)  |
| `POST`   | `/api/bookmarks/{mainSku}/persist`     | Gia hạn/Chuyển bookmark từ Staging sang chính thức (7 ngày)   | **7 ngày**                             |
| `GET`    | `/api/bookmarks/{mainSku}`             | Lấy chi tiết phụ kiện đã lưu (kèm giá & tồn kho realtime)     | Trả về thời gian còn lại               |
| `GET`    | `/api/bookmarks`                       | Lấy toàn bộ danh sách bookmark của người dùng/guest           | —                                      |
| `DELETE` | `/api/bookmarks/{mainSku}/items/{sku}` | Xóa 1 phụ kiện khỏi Bookmark của sản phẩm chính               | —                                      |
| `DELETE` | `/api/bookmarks/{mainSku}`             | Xóa toàn bộ Bookmark của sản phẩm chính                       | —                                      |

---

## 3. Đặc tả chi tiết từng Endpoint

### 3.1. Thêm / Cộng dồn phụ kiện khi bấm `+` (Persist - TTL 7 ngày, Fixed Window)

Thêm một sản phẩm phụ kiện vào gói Bookmark gắn với sản phẩm chính (`mainSku`). Sử dụng cơ chế Atomic Increment (`HINCRBY`) trên Redis Hash.

> **Quy tắc Fixed Window (Không Reset Time):** TTL 7 ngày (604,800s) chỉ được thiết lập ở lần tạo Bookmark đầu tiên. Các lần bấm `+` thêm phụ kiện tiếp theo sẽ **KHÔNG gia hạn hay reset lại thời gian**, đảm bảo mốc hết hạn ban đầu được giữ nguyên cố định.

- **Endpoint:** `POST /api/bookmarks/{mainSku}/items`
- **Path Parameters:**
  - `mainSku` *(string, required)*: SKU của sản phẩm chính trên PDP (Ví dụ: `OPPO-FIND-X8-PRO-BLK`). Bắt buộc phải tồn tại trong hệ thống.
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Request Body:

```json
{
  "sku": "STRAP-APW-01",
  "quantity": 1
}
```

#### Field Constraints:

- `sku`: Bắt buộc, không được để trống. Phải tồn tại trong DB và ở trạng thái kho `AVAILABLE`.
- `quantity`: Bắt buộc, số nguyên dương `1 <= quantity <= 99`.

#### Success Response (`200 OK`):

Trả về thông tin phụ kiện vừa được thêm/cộng dồn theo cấu trúc `BookmarkItemResponse`:

```json
{
  "status": {
    "code": 200,
    "message": "Thêm phụ kiện vào bookmark thành công"
  },
  "data": {
    "sku": "STRAP-APW-01",
    "productName": "Dây đeo Apple Watch Sport Band",
    "imageUrl": "https://cdn.example.com/images/strap-01.jpg",
    "attributesTitle": "Size 45mm - Màu Cam",
    "unitPrice": 570000.0,
    "salePrice": 513000.0,
    "quantity": 2,
    "subTotal": 1026000.0,
    "isAvailable": true,
    "stock": 999
  }
}
```

---

### 3.2. Tự động lưu danh sách chuẩn bị (Staging - TTL 1 giờ, Fixed Window)

Dành cho kịch bản người dùng chọn thử nhiều phụ kiện trên giao diện (debounced auto-save). Hệ thống lưu tạm vào Redis với TTL ngắn hạn (3600s). Nếu người dùng không chốt, dữ liệu sẽ tự hủy sau 1 giờ.

> **Quy tắc Fixed Window:** Khi client gọi debounced auto-sync liên tục trong phiên duyệt, TTL còn lại được bảo toàn, **không reset lại về 3600s**.

- **Endpoint:** `POST /api/bookmarks/{mainSku}/staging`
- **Path Parameters:**
  - `mainSku` *(string, required)*: SKU của sản phẩm chính. Bắt buộc tồn tại trong hệ thống.
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Request Body:

```json
{
  "items": [
    { "sku": "STRAP-APW-01", "quantity": 1 },
    { "sku": "CHARGER-65W-02", "quantity": 1 }
  ]
}
```

#### Success Response (`200 OK`):

Trả về toàn bộ chi tiết bookmark tạm thời cùng giá & chiết khấu realtime:

```json
{
  "status": {
    "code": 200,
    "message": "Tự động lưu danh sách chuẩn bị thành công"
  },
  "data": {
    "mainSku": "OPPO-FIND-X8-PRO-BLK",
    "totalItems": 2,
    "totalPrice": 1227000.0,
    "totalSalePrice": 675000.0,
    "totalDiscount": 552000.0,
    "ttlSecondsRemaining": 3540,
    "expiresAtEpochMs": 1725704940000,
    "formattedRemainingTime": "59 phút",
    "items": [
      {
        "sku": "STRAP-APW-01",
        "productName": "Dây đeo Apple Watch Sport Band",
        "imageUrl": "https://cdn.example.com/images/strap-01.jpg",
        "attributesTitle": "Size 45mm - Màu Cam",
        "unitPrice": 570000.0,
        "salePrice": 513000.0,
        "quantity": 1,
        "subTotal": 513000.0,
        "isAvailable": true,
        "stock": 999
      },
      {
        "sku": "CHARGER-65W-02",
        "productName": "Củ sạc SuperVOOC 65W",
        "imageUrl": "https://cdn.example.com/images/charger-65w.jpg",
        "attributesTitle": "Trắng",
        "unitPrice": 657000.0,
        "salePrice": 162000.0,
        "quantity": 1,
        "subTotal": 162000.0,
        "isAvailable": true,
        "stock": 999
      }
    ]
  }
}
```

---

### 3.3. Gia hạn / Chuyển Bookmark sang 7 ngày (Persist)

Chuyển đổi danh sách đang chọn thử (staging) thành bookmark chính thức lưu trữ dài hạn (7 ngày).

- **Endpoint:** `POST /api/bookmarks/{mainSku}/persist`
- **Path Parameters:**
  - `mainSku` *(string, required)*: SKU của sản phẩm chính. Bắt buộc tồn tại trong hệ thống.
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Success Response (`200 OK`):

```json
{
  "status": {
    "code": 200,
    "message": "Đã lưu bookmark vào hệ thống trong 7 ngày"
  },
  "data": {
    "mainSku": "OPPO-FIND-X8-PRO-BLK",
    "totalItems": 2,
    "totalPrice": 1227000.0,
    "totalSalePrice": 675000.0,
    "totalDiscount": 552000.0,
    "ttlSecondsRemaining": 604800,
    "expiresAtEpochMs": 1726306200000,
    "formattedRemainingTime": "7 ngày",
    "items": [ ... ]
  }
}
```

---

### 3.4. Lấy chi tiết Bookmark theo sản phẩm chính (GET)

Gọi khi người dùng tải trang PDP hoặc quay lại sau 5–10 tiếng. Backend đọc danh sách từ Redis, tự động loại bỏ (auto-clean) các SKU đã bị xóa khỏi hệ thống, sau đó query Database theo thời gian thực để enrich thông tin giá bán mới nhất, trạng thái tồn kho và **tính toán thời gian hết hạn còn lại**.

Nếu chưa có bookmark dài hạn, hệ thống sẽ tự động tìm kiếm trong staging items đang lưu tạm để khôi phục cho người dùng.

- **Endpoint:** `GET /api/bookmarks/{mainSku}`
- **Path Parameters:**
  - `mainSku` *(string, required)*: SKU của sản phẩm chính. Bắt buộc tồn tại trong hệ thống (ném `404 ATTRIBUTES_NOT_FOUND` nếu không tồn tại).
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Success Response (`200 OK`):

```json
{
  "status": {
    "code": 200,
    "message": "Success"
  },
  "data": {
    "mainSku": "OPPO-FIND-X8-PRO-BLK",
    "totalItems": 2,
    "totalPrice": 1227000.0,
    "totalSalePrice": 675000.0,
    "totalDiscount": 552000.0,
    "ttlSecondsRemaining": 576000,
    "expiresAtEpochMs": 1726277400000,
    "formattedRemainingTime": "6 ngày 16 giờ",
    "items": [
      {
        "sku": "STRAP-APW-01",
        "productName": "Dây đeo Apple Watch Sport Band",
        "imageUrl": "https://cdn.example.com/images/strap-01.jpg",
        "attributesTitle": "Size 45mm - Màu Cam",
        "unitPrice": 570000.0,
        "salePrice": 513000.0,
        "quantity": 1,
        "subTotal": 513000.0,
        "isAvailable": true,
        "stock": 999
      },
      {
        "sku": "CHARGER-65W-02",
        "productName": "Củ sạc SuperVOOC 65W",
        "imageUrl": "https://cdn.example.com/images/charger-65w.jpg",
        "attributesTitle": "Trắng",
        "unitPrice": 657000.0,
        "salePrice": 162000.0,
        "quantity": 1,
        "subTotal": 162000.0,
        "isAvailable": false,
        "stock": 0
      }
    ]
  }
}
```

*Ghi chú: Nếu `mainSku` hợp lệ trong DB nhưng chưa có phụ kiện nào được lưu, API trả về `totalItems = 0` và `items: []`.*

---

### 3.5. Lấy tất cả Bookmark của người dùng / guest (GET All)

Lấy toàn bộ các sản phẩm chính và phụ kiện mua cùng đã lưu của người dùng hiện tại hoặc guest session.

- **Endpoint:** `GET /api/bookmarks`
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Success Response (`200 OK`):

```json
{
  "status": {
    "code": 200,
    "message": "Success"
  },
  "data": [
    {
      "mainSku": "OPPO-FIND-X8-PRO-BLK",
      "totalItems": 2,
      "totalPrice": 1227000.0,
      "totalSalePrice": 675000.0,
      "totalDiscount": 552000.0,
      "ttlSecondsRemaining": 576000,
      "expiresAtEpochMs": 1726277400000,
      "formattedRemainingTime": "6 ngày 16 giờ",
      "items": [ ... ]
    }
  ]
}
```

---

### 3.6. Xóa 1 phụ kiện khỏi Bookmark

- **Endpoint:** `DELETE /api/bookmarks/{mainSku}/items/{sku}`
- **Path Parameters:**
  - `mainSku` *(string, required)*: SKU của sản phẩm chính.
  - `sku` *(string, required)*: SKU phụ kiện cần loại bỏ.
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Success Response (`200 OK`):

```json
{
  "status": {
    "code": 200,
    "message": "Đã xóa phụ kiện khỏi bookmark"
  }
}
```

---

### 3.7. Xóa toàn bộ Bookmark của sản phẩm chính

- **Endpoint:** `DELETE /api/bookmarks/{mainSku}`
- **Path Parameters:**
  - `mainSku` *(string, required)*: SKU của sản phẩm chính.
- **Headers:**
  - `Authorization: Bearer <token>` **HOẶC** `X-Guest-Id: <uuid>`

#### Success Response (`200 OK`):

```json
{
  "status": {
    "code": 200,
    "message": "Đã xóa toàn bộ bookmark cho sản phẩm chính"
  }
}
```

---

## 4. Bảng mã lỗi & Định dạng Exception (RFC 7807 Problem Details)

Khi gặp lỗi nghiệp vụ, hệ thống trả về cấu trúc lỗi tiêu chuẩn **RFC 7807 Problem Details**:

```json
{
  "type": "about:blank",
  "title": "Thuộc tính không tồn tại",
  "status": 404,
  "detail": "Sản phẩm chính [NON-EXISTENT-SKU] không tồn tại trong hệ thống",
  "errorCode": "ATTRIBUTES_NOT_FOUND"
}
```

### Bảng mã lỗi chi tiết:

| HTTP Status        | Error Code                | Nguyên nhân                                                | Hướng dẫn khắc phục                     |
| ------------------ | ------------------------- | ---------------------------------------------------------- | --------------------------------------- |
| `400 Bad Request`  | `VALIDATION_FAILED`       | `mainSku` hoặc `sku` để trống; `quantity <= 0` hoặc `> 99` | Kiểm tra payload và path parameter      |
| `400 Bad Request`  | `ATTRIBUTES_OUT_OF_STOCK` | Phụ kiện đã hết hàng hoặc trạng thái khác `AVAILABLE`      | Ẩn nút thêm hoặc hiển thị báo hết hàng  |
| `404 Not Found`    | `ATTRIBUTES_NOT_FOUND`    | `mainSku` hoặc `sku` không tồn tại trong hệ thống          | Kiểm tra lại mã SKU                     |
| `401 Unauthorized` | `UNAUTHORIZED`            | Thiếu cả JWT Bearer token và header `X-Guest-Id`           | Bổ sung header định danh người dùng     |

---

## 5. Code Examples

### 5.1. cURL

**Thêm phụ kiện khi bấm `+` (Guest):**

```bash
curl -X POST http://localhost:8080/api/bookmarks/OPPO-FIND-X8-PRO-BLK/items \
  -H "Content-Type: application/json" \
  -H "X-Guest-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "sku": "STRAP-APW-01",
    "quantity": 1
  }'
```

**Lấy chi tiết Bookmark sau 5–10 tiếng:**

```bash
curl -X GET http://localhost:8080/api/bookmarks/OPPO-FIND-X8-PRO-BLK \
  -H "X-Guest-Id: 550e8400-e29b-41d4-a716-446655440000"
```

**Lưu tạm nhiều phụ kiện (Auto-save):**

```bash
curl -X POST http://localhost:8080/api/bookmarks/OPPO-FIND-X8-PRO-BLK/staging \
  -H "Content-Type: application/json" \
  -H "X-Guest-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -d '{
    "items": [
      { "sku": "STRAP-APW-01", "quantity": 1 },
      { "sku": "CHARGER-65W-02", "quantity": 1 }
    ]
  }'
```

**Xóa toàn bộ Bookmark của sản phẩm chính:**

```bash
curl -X DELETE http://localhost:8080/api/bookmarks/OPPO-FIND-X8-PRO-BLK \
  -H "X-Guest-Id: 550e8400-e29b-41d4-a716-446655440000"
```

---

### 5.2. JavaScript (Fetch / Axios)

```javascript
const API_BASE = 'http://localhost:8080/api/bookmarks';
const guestId = localStorage.getItem('guest_session_id') || 'guest-uuid-123';

// 1. Thêm phụ kiện vào bookmark dài hạn khi click (+)
async function addAccessory(mainSku, accessorySku, quantity = 1) {
  const res = await fetch(`${API_BASE}/${encodeURIComponent(mainSku)}/items`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Guest-Id': guestId
    },
    body: JSON.stringify({ sku: accessorySku, quantity })
  });
  return await res.json();
}

// 2. Load bookmark khi mở lại PDP sau 5 - 10 tiếng
async function loadBookmarkDetails(mainSku) {
  const res = await fetch(`${API_BASE}/${encodeURIComponent(mainSku)}`, {
    method: 'GET',
    headers: {
      'X-Guest-Id': guestId
    }
  });
  const json = await res.json();
  if (json.status?.code === 200 && json.data?.totalItems > 0) {
    console.log(`Đã khôi phục ${json.data.totalItems} phụ kiện đã chọn!`, json.data.items);
  }
  return json.data;
}

// 3. Xóa một phụ kiện khỏi bookmark
async function removeAccessory(mainSku, accessorySku) {
  const res = await fetch(`${API_BASE}/${encodeURIComponent(mainSku)}/items/${encodeURIComponent(accessorySku)}`, {
    method: 'DELETE',
    headers: {
      'X-Guest-Id': guestId
    }
  });
  return await res.json();
}
```

---

### 5.3. Python (Requests)

```python
import requests

BASE_URL = "http://localhost:8080/api/bookmarks"
HEADERS = {
    "Content-Type": "application/json",
    "X-Guest-Id": "test-guest-uuid-001"
}

# 1. Thêm 1 phụ kiện
payload = {"sku": "STRAP-APW-01", "quantity": 1}
response = requests.post(f"{BASE_URL}/OPPO-FIND-X8-PRO-BLK/items", json=payload, headers=HEADERS)
print("Add status:", response.status_code, response.json())

# 2. Lấy dữ liệu khôi phục sau vài tiếng
get_response = requests.get(f"{BASE_URL}/OPPO-FIND-X8-PRO-BLK", headers=HEADERS)
res_json = get_response.json()
bookmark_data = res_json.get("data", {})
print(f"Total items: {bookmark_data.get('totalItems')}, Total sale price: {bookmark_data.get('totalSalePrice')}")
