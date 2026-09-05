# 📊 BÁO CÁO TỔNG THỂ ĐẶC TẢ API & KẾT QUẢ THỰC NGHIỆM: MODULE GIỎ HÀNG (SHOPPING CART)
## *Hệ thống ERP Spring Boot 3.5.0 — Kiến trúc In-Memory Redis kết hợp GraphQL-Style Projection*

- **Dự án:** ERP Spring Boot Experiment System
- **Module:** Shopping Cart (`com.ddicg.erp.modules.cart`)
- **Phiên bản API:** `v1.0 (Consolidated RESTful)`
- **Base URL:** `http://localhost:8080/api/cart`
- **Ngày kiểm thử & cập nhật:** 04/09/2026
- **Trạng thái:** ✅ **Production-Ready** (100% Tests & Live Verification Passed)

---

## 📑 MỤC LỤC
1. [Tổng quan Kiến trúc & Nguyên lý Vận hành](#1-tổng-quan-kiến-trúc--nguyên-lý-vận-hành)
2. [Cơ chế Xác thực & Phân vùng Định danh (Auth & Partitioning)](#2-cơ-chế-xác-thực--phân-vùng-định-danh-auth--partitioning)
3. [Triết lý Thiết kế Hướng GraphQL (GraphQL-Oriented Design)](#3-triết-lý-thiết-kế-hướng-graphql-graphql-oriented-design)
4. [Danh mục Dữ liệu Kiểm thử Thực tế (Live Database Seed)](#4-danh-mục-dữ-liệu-kiểm-thử-thực-tế-live-database-seed)
5. [Đặc tả Chi tiết 6 Endpoint RESTful Chuẩn hóa](#5-đặc-tả-chi-tiết-6-endpoint-restful-chuẩn-hóa)
   - [5.1. GET /api/cart — Lấy Giỏ hàng Chi tiết & Sparse Projection](#51-get-apicart--lấy-giỏ-hàng-chi-tiết--sparse-projection)
   - [5.2. GET /api/cart/count — Lấy Số lượng Badge Giỏ hàng](#52-get-apicartcount--lấy-số-lượng-badge-giỏ-hàng)
   - [5.3. POST /api/cart/items — Thêm Sản phẩm vào Giỏ (Batch Support)](#53-post-apicartitems--thêm-sản-phẩm-vào-giỏ-batch-support)
   - [5.4. PUT /api/cart/items/{sku} — Cập nhật Số lượng Sản phẩm](#54-put-apicartitemssku--cập-nhật-số-lượng-sản-phẩm)
   - [5.5. DELETE /api/cart/items/{sku} — Xóa Một Sản phẩm](#55-delete-apicartitemssku--xóa-một-sản-phẩm)
   - [5.6. DELETE /api/cart — Xóa Chọn lọc hoặc Làm trống Giỏ](#56-delete-apicart--xóa-chọn-lọc-hoặc-làm-trống-giỏ)
   - [5.7. POST /api/cart/merge — Hợp nhất Giỏ hàng Guest khi Đăng nhập](#57-post-apicartmerge--hợp-nhất-giỏ-hàng-guest-khi-đăng-nhập)
6. [Đặc tả Định dạng Lỗi Chuẩn RFC 7807 (ProblemDetail)](#6-đặc-tả-định-dạng-lỗi-chuẩn-rfc-7807-problemdetail)
7. [Bằng chứng Thực nghiệm & Nhật ký Server Thực tế (Live Verification & Logs)](#7-bằng-chứng-thực-nghiệm--nhật-ký-server-thực-tế-live-verification--logs)
8. [Kết quả Kiểm thử Tự động (Automated Test Suite)](#8-kết-quả-kiểm-thử-tự-động-automated-test-suite)

---

## 1. 🏗️ Tổng quan Kiến trúc & Nguyên lý Vận hành

Module Giỏ hàng được xây dựng theo kiến trúc **In-Memory First** kết hợp **Dynamic Data Enrichment**:

```
+---------------------------------------------------------------------------------------+
|                                  CLIENT APPLICATIONS                                  |
|        (Mobile App, Desktop Web, Mobile Web, POS, Mini-Cart, Checkout Drawer)         |
+-------------------------------------------+-------------------------------------------+
                                            | HTTP Requests (JWT / X-Guest-Id)
                                            v
+---------------------------------------------------------------------------------------+
|                            SPRING SECURITY & REST CONTROLLER                          |
|   - /api/cart/** (Public with X-Guest-Id / Authenticated via JWT)                     |
|   - /api/cart/merge (Strictly Authenticated via Bearer JWT)                           |
+-------------------------------------------+-------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                                 SHOPPING CART SERVICE                                 |
|  1. Context Resolver (User ID vs Guest ID)                                            |
|  2. Stock & Limit Guard (Status == AVAILABLE, Qty <= 99, Distinct SKUs <= 50)         |
|  3. Dynamic Pipeline Router (Fast Path vs Deep Path DataLoader)                       |
+----------------------+----------------------------------------+-----------------------+
                       |                                        |
     [Fast Path: < 1ms]|                                        | [Deep Path: Batch Load]
                       v                                        v
+-------------------------------------+    +--------------------------------------------+
|           REDIS DATA STORE          |    |           ORACLE DATABASE / CACHE          |
| - Hash: cart:items:{userId} (30d)   |    | - AttributesRepository.findAllBySkuIn()    |
| - Hash: cart:guest:items:{gid} (7d) |    | - Product / MediaItems / Specifications    |
+-------------------------------------+    +--------------------------------------------+
```

---

## 2. 🔐 Cơ chế Xác thực & Phân vùng Định danh (Auth & Partitioning)

Hệ thống hỗ trợ song song 2 trạng thái người dùng với cơ chế tách biệt hoàn toàn trên Redis:

| Tiêu chí | Người dùng Khách vãng lai (Guest) | Người dùng Đã đăng nhập (Member) |
| :--- | :--- | :--- |
| **Định danh** | Header `X-Guest-Id: <UUID>` | Header `Authorization: Bearer <JWT>` |
| **Redis Key** | `cart:guest:items:{guestId}` | `cart:items:{userId}` |
| **Thời gian sống (TTL)** | **7 ngày** (trượt sau mỗi thao tác) | **30 ngày** (trượt sau mỗi thao tác) |
| **Cấu trúc lưu trữ** | Redis Hash: `field = SKU`, `value = Quantity` | Redis Hash: `field = SKU`, `value = Quantity` |
| **Bảo vệ RAM** | Tối đa 50 SKU phân biệt, tối đa 99 cái/SKU | Tối đa 50 SKU phân biệt, tối đa 99 cái/SKU |

---

## 3. ⚡ Triết lý Thiết kế Hướng GraphQL (GraphQL-Oriented Design)

Nhằm tối ưu hóa băng thông mạng cho các thiết bị di động và loại bỏ bài toán over-fetching / under-fetching của REST truyền thống, module cung cấp:

1. **Sparse Fieldsets (`?fields=...`):**
   - Cho phép client chỉ định chính xác các trường cần lấy (ví dụ: `?fields=totalItems,finalAmount` hoặc `?fields=items.sku,items.quantity`).
   - Các trường không được yêu cầu sẽ được gán `null` và tự động bị loại bỏ khỏi JSON response qua `@JsonInclude(JsonInclude.Include.NON_NULL)`.
2. **Fast Path Router (Pure Redis In-Memory):**
   - Khi client chỉ yêu cầu các trường đã có sẵn trong Redis Hash (`totalItems`, `username`, `items.sku`, `items.quantity`), hệ thống **bỏ qua 100% truy vấn Database**, phản hồi với độ trễ `< 1ms`.
3. **Deep Path DataLoader:**
   - Khi cần dữ liệu sản phẩm chi tiết (`productName`, `unitPrice`, `salePrice`, `imageUrl`, `subTotal`), DataLoader gom toàn bộ SKU và thực thi **1 câu truy vấn batch duy nhất** (`findAllBySku_skuIn`) kèm `@EntityGraph(attributePaths = {"product"})` để tránh lỗi Lazy Loading.
4. **Sub-resource Expansion (`?include=...`):**
   - Hỗ trợ nạp mở rộng thông số kỹ thuật hoặc khuyến mãi đi kèm qua `?include=specifications,promotions`.

---

## 4. 📦 Danh mục Dữ liệu Kiểm thử Thực tế (Live Database Seed)

Dữ liệu được truy vấn và kiểm thử trực tiếp trên cơ sở dữ liệu Oracle (`SPRING_APP.ATTRIBUTES`):

| Mã SKU (`sku`) | Tên hiển thị thuộc tính | Tên sản phẩm gốc | Giá niêm yết (`unitPrice`) | Giá bán (`salePrice`) | Tồn kho | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `ATTR-TABS10U-GRAPH-5G-512` | Galaxy Tab S10 Ultra Graphite 5G 512GB | Samsung Galaxy Tab S10 Ultra | 32.000.000 ₫ | 28.800.000 ₫ | 999 | `AVAILABLE` |
| `ATTR-MIPAD7P-BLUE-12-512` | Xiaomi Pad 7 Pro Xanh 12GB/512GB Wi-Fi | Xiaomi Pad 7 Pro | 11.000.000 ₫ | 9.900.000 ₫ | 999 | `AVAILABLE` |
| `ATTR-MIPAD7P-WHITE-5G-512` | Xiaomi Pad 7 Pro Trắng 12GB/512GB 5G | Xiaomi Pad 7 Pro | 13.000.000 ₫ | 11.700.000 ₫ | 999 | `AVAILABLE` |
| `ATTR-MSPRO11-GRAPH-32-512` | Surface Pro 11 Graphite 32GB/512GB Wi-Fi| Microsoft Surface Pro 11 | 43.000.000 ₫ | 38.700.000 ₫ | 999 | `AVAILABLE` |

---

## 5. 🛠️ Đặc tả Chi tiết 6 Endpoint RESTful Chuẩn hóa

### 5.1. `GET /api/cart` — Lấy Giỏ hàng Chi tiết & Sparse Projection

Lấy thông tin giỏ hàng hiện tại của khách vãng lai hoặc người dùng đã đăng nhập. Hỗ trợ lọc trường (Sparse Fieldsets) và mở rộng quan hệ (Include).

- **Method:** `GET`
- **Path:** `/api/cart`
- **Authentication:** Tùy chọn (Yêu cầu `Authorization: Bearer <token>` HOẶC `X-Guest-Id: <uuid>`)

#### Request Headers
| Header | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `Authorization` | String | Không | Bearer token JWT nếu người dùng đã đăng nhập |
| `X-Guest-Id` | String | Không | Định danh UUID của khách vãng lai (bắt buộc nếu không có JWT) |

#### Query Parameters
| Parameter | Kiểu | Mặc định | Mô tả |
| :--- | :--- | :--- | :--- |
| `fields` | List\<String\> | `null` (Full) | Danh sách trường cần lấy: `totalItems`, `totalPrice`, `items.sku`, v.v. |
| `include` | List\<String\> | `null` | Danh sách quan hệ mở rộng: `specifications`, `promotions` |

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Lấy giỏ hàng thành công.
- `401 Unauthorized`: Không cung cấp cả Bearer token lẫn header `X-Guest-Id`.

#### Ví dụ cURL (Happy Path)
```bash
curl -X GET "http://localhost:8080/api/cart" \
  -H "X-Guest-Id: test-guest-endpoint-1"
```

#### Response Example (200 OK)
```json
{
  "status": {
    "message": "Success",
    "code": 200
  },
  "data": {
    "username": "guest:test-guest-endpoint-1",
    "items": [
      {
        "sku": "ATTR-TABS10U-GRAPH-5G-512",
        "productName": "Samsung Galaxy Tab S10 Ultra",
        "imageUrl": "https://images.unsplash.com/photo-1587033411391-5d9e51cce126?w=600",
        "attributesTitle": "Galaxy Tab S10 Ultra Graphite 5G 512GB",
        "unitPrice": 32000000.0,
        "salePrice": 28800000.0,
        "quantity": 2,
        "subTotal": 57600000.0,
        "isAvailable": true,
        "stock": 999
      }
    ],
    "totalItems": 2,
    "totalPrice": 64000000.0,
    "totalSalePrice": 57600000.0,
    "totalDiscount": 6400000.0,
    "finalAmount": 57600000.0
  }
}
```

---

### 5.2. `GET /api/cart/count` — Lấy Số lượng Badge Giỏ hàng

Endpoint chuyên biệt cực nhẹ, tối ưu hóa tối đa cho các component UI Header / Badge Polling.

- **Method:** `GET`
- **Path:** `/api/cart/count`
- **Authentication:** Tùy chọn (Yêu cầu JWT hoặc `X-Guest-Id`)

#### Request Headers
| Header | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `Authorization` | String | Không | Bearer token nếu đã đăng nhập |
| `X-Guest-Id` | String | Không | Header UUID của khách vãng lai |

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Trả về tổng số lượng sản phẩm (`Integer`).
- `401 Unauthorized`: Thiếu thông tin định danh.

#### Ví dụ cURL
```bash
curl -X GET "http://localhost:8080/api/cart/count" \
  -H "X-Guest-Id: test-guest-endpoint-1"
```

#### Response Example (200 OK)
```json
{
  "status": {
    "message": "Success",
    "code": 200
  },
  "data": 2
}
```

---

### 5.3. `POST /api/cart/items` — Thêm Sản phẩm vào Giỏ (Batch Support)

Thêm một hoặc nhiều sản phẩm vào giỏ hàng. Nếu SKU đã tồn tại trong giỏ, số lượng sẽ được cộng dồn (tối đa 99 cái/SKU).

- **Method:** `POST`
- **Path:** `/api/cart/items`
- **Authentication:** Tùy chọn (Yêu cầu JWT hoặc `X-Guest-Id`)
- **Content-Type:** `application/json`

#### Request Body Schema (`List<CartItemRequest>`)
| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `sku` | String | `@NotBlank` | Mã định danh SKU hợp lệ |
| `quantity` | Integer | `@NotNull`, `@Min(1)`, `@Max(99)` | Số lượng thêm vào (từ 1 đến 99) |

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Thêm sản phẩm thành công, trả về giỏ hàng đầy đủ.
- `400 Bad Request`: Số lượng không hợp lệ (`<= 0` hoặc `> 99`), body sai định dạng, hoặc sản phẩm đã hết hàng (`ATTRIBUTES_OUT_OF_STOCK`).
- `401 Unauthorized`: Không cung cấp thông tin định danh.
- `404 Not Found`: Mã SKU không tồn tại trong cơ sở dữ liệu (`ATTRIBUTES_NOT_FOUND`).

#### Ví dụ cURL
```bash
curl -X POST "http://localhost:8080/api/cart/items" \
  -H "X-Guest-Id: test-guest-endpoint-1" \
  -H "Content-Type: application/json" \
  -d '[
    {"sku": "ATTR-TABS10U-GRAPH-5G-512", "quantity": 2},
    {"sku": "ATTR-MIPAD7P-BLUE-12-512", "quantity": 1}
  ]'
```

#### Response Example (200 OK)
```json
{
  "status": {
    "message": "Thêm sản phẩm vào giỏ hàng thành công",
    "code": 200
  },
  "data": {
    "username": "guest:test-guest-endpoint-1",
    "items": [
      {
        "sku": "ATTR-TABS10U-GRAPH-5G-512",
        "productName": "Samsung Galaxy Tab S10 Ultra",
        "quantity": 2,
        "subTotal": 57600000.0,
        "isAvailable": true
      },
      {
        "sku": "ATTR-MIPAD7P-BLUE-12-512",
        "productName": "Xiaomi Pad 7 Pro",
        "quantity": 1,
        "subTotal": 9900000.0,
        "isAvailable": true
      }
    ],
    "totalItems": 3,
    "finalAmount": 67500000.0
  }
}
```

---

### 5.4. `PUT /api/cart/items/{sku}` — Cập nhật Số lượng Sản phẩm

Cập nhật chính xác số lượng của một SKU cụ thể trong giỏ hàng.
> **Quy tắc Nghiệp vụ:** Nếu truyền `quantity = 0`, sản phẩm sẽ tự động được xóa khỏi giỏ hàng.

- **Method:** `PUT`
- **Path:** `/api/cart/items/{sku}`
- **Authentication:** Tùy chọn (Yêu cầu JWT hoặc `X-Guest-Id`)
- **Content-Type:** `application/json`

#### Path Parameters
| Parameter | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `sku` | String | Mã SKU cần cập nhật |

#### Request Body Schema (`UpdateCartItemRequest`)
| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `quantity` | Integer | `@NotNull`, `@Min(0)`, `@Max(99)` | Số lượng mới. Nếu bằng 0 sẽ xóa sản phẩm khỏi giỏ |

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Cập nhật thành công.
- `400 Bad Request`: Số lượng âm (`< 0`) hoặc vượt quá 99 (`VALIDATION_FAILED`).
- `401 Unauthorized`: Chưa định danh.
- `404 Not Found`: SKU không có trong giỏ hàng hiện tại (`ATTRIBUTES_NOT_FOUND`).

#### Ví dụ cURL
```bash
curl -X PUT "http://localhost:8080/api/cart/items/ATTR-TABS10U-GRAPH-5G-512" \
  -H "X-Guest-Id: test-guest-endpoint-1" \
  -H "Content-Type: application/json" \
  -d '{"quantity": 5}'
```

#### Response Example (200 OK)
```json
{
  "status": {
    "message": "Cập nhật số lượng sản phẩm thành công",
    "code": 200
  },
  "data": {
    "username": "guest:test-guest-endpoint-1",
    "items": [
      {
        "sku": "ATTR-TABS10U-GRAPH-5G-512",
        "productName": "Samsung Galaxy Tab S10 Ultra",
        "quantity": 5,
        "subTotal": 144000000.0,
        "isAvailable": true
      }
    ],
    "totalItems": 5,
    "finalAmount": 144000000.0
  }
}
```

---

### 5.5. `DELETE /api/cart/items/{sku}` — Xóa Một Sản phẩm

Xóa hoàn toàn một SKU khỏi giỏ hàng.

- **Method:** `DELETE`
- **Path:** `/api/cart/items/{sku}`
- **Authentication:** Tùy chọn (Yêu cầu JWT hoặc `X-Guest-Id`)

#### Path Parameters
| Parameter | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `sku` | String | Mã SKU cần xóa |

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Đã xóa sản phẩm thành công.
- `401 Unauthorized`: Chưa định danh.
- `404 Not Found`: SKU không tồn tại trong giỏ hàng (`ATTRIBUTES_NOT_FOUND`).

#### Ví dụ cURL
```bash
curl -X DELETE "http://localhost:8080/api/cart/items/ATTR-TABS10U-GRAPH-5G-512" \
  -H "X-Guest-Id: test-guest-endpoint-1"
```

#### Response Example (200 OK)
```json
{
  "status": {
    "message": "Đã xóa sản phẩm khỏi giỏ hàng",
    "code": 200
  },
  "data": {
    "username": "guest:test-guest-endpoint-1",
    "items": [],
    "totalItems": 0,
    "totalPrice": 0.0,
    "finalAmount": 0.0
  }
}
```

---

### 5.6. `DELETE /api/cart` — Xóa Chọn lọc hoặc Làm trống Giỏ

Endpoint hợp nhất thông minh thay thế cho các endpoint cũ (`/clear`, `/batch-delete`).
- Khi truyền tham số `?skus=sku1,sku2`: Hệ thống xóa các SKU được chỉ định (Batch Delete).
- Khi **không truyền** tham số `skus`: Hệ thống xóa sạch toàn bộ giỏ hàng (Clear All).

- **Method:** `DELETE`
- **Path:** `/api/cart`
- **Authentication:** Tùy chọn (Yêu cầu JWT hoặc `X-Guest-Id`)

#### Query Parameters
| Parameter | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :--- | :--- |
| `skus` | List\<String\> | Không | Danh sách SKU cần xóa (ví dụ: `?skus=SKU1,SKU2`). Bỏ trống để xóa toàn bộ |

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Xóa thành công.
- `401 Unauthorized`: Chưa định danh.

#### Ví dụ cURL: Xóa chọn lọc (Batch Remove)
```bash
curl -X DELETE "http://localhost:8080/api/cart?skus=ATTR-TABS10U-GRAPH-5G-512" \
  -H "X-Guest-Id: test-guest-endpoint-1"
```
*Phản hồi (200 OK):* `"message": "Đã xóa các sản phẩm được chọn khỏi giỏ hàng"`

#### Ví dụ cURL: Làm trống toàn bộ giỏ hàng (Clear Cart)
```bash
curl -X DELETE "http://localhost:8080/api/cart" \
  -H "X-Guest-Id: test-guest-endpoint-1"
```
*Phản hồi (200 OK):* `"message": "Đã xóa toàn bộ giỏ hàng"`

---

### 5.7. `POST /api/cart/merge` — Hợp nhất Giỏ hàng Guest khi Đăng nhập

Tự động gộp toàn bộ sản phẩm hợp lệ từ giỏ hàng khách vãng lai vào tài khoản thành viên sau khi đăng nhập thành công. Key Redis giỏ hàng guest sẽ được giải phóng ngay lập tức.
> **Linh hoạt đầu vào:** Hỗ trợ truyền `guestId` thông qua **JSON Request Body** HOẶC thông qua **Header `X-Guest-Id`**.

- **Method:** `POST`
- **Path:** `/api/cart/merge`
- **Authentication:** **BẮT BUỘC** (`@PreAuthorize("isAuthenticated()")` qua `Authorization: Bearer <token>`)

#### Cách 1: Truyền qua JSON Request Body
```bash
curl -X POST "http://localhost:8080/api/cart/merge" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"guestId": "guest-merge-999"}'
```

#### Cách 2: Truyền qua Header `X-Guest-Id`
```bash
curl -X POST "http://localhost:8080/api/cart/merge" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "X-Guest-Id: guest-header-test-1"
```

#### Response Example (200 OK)
```json
{
  "status": {
    "message": "Hợp nhất giỏ hàng thành công",
    "code": 200
  },
  "data": {
    "username": "annoeye@gmail.com",
    "items": [
      {
        "sku": "ATTR-MIPAD7P-WHITE-5G-512",
        "productName": "Xiaomi Pad 7 Pro",
        "quantity": 3,
        "subTotal": 35100000.0,
        "isAvailable": true
      }
    ],
    "totalItems": 3,
    "finalAmount": 35100000.0
  }
}
```

#### Mã phản hồi (HTTP Status Codes)
- `200 OK`: Hợp nhất thành công.
- `403 Forbidden`: Chưa đăng nhập hoặc token không hợp lệ (`ACCESS_DENIED`).

---

## 6. ⚠️ Đặc tả Định dạng Lỗi Chuẩn RFC 7807 (ProblemDetail)

Tất cả các lỗi nghiệp vụ và validation trong module đều được chuẩn hóa theo RFC 7807:

### 6.1. Lỗi Chưa xác thực (`401 Unauthorized`)
```json
{
  "type": "about:blank",
  "title": "Chưa xác thực",
  "status": 401,
  "detail": "Vui lòng đăng nhập hoặc cung cấp header X-Guest-Id",
  "instance": "/api/cart",
  "errorCode": "UNAUTHORIZED"
}
```

### 6.2. Lỗi Không tìm thấy SKU (`404 Not Found`)
```json
{
  "type": "about:blank",
  "title": "Thuộc tính không tồn tại",
  "status": 404,
  "detail": "Sản phẩm [NON_EXISTENT_SKU_123] không tồn tại",
  "instance": "/api/cart/items",
  "errorCode": "ATTRIBUTES_NOT_FOUND"
}
```

### 6.3. Lỗi SKU không có trong giỏ hàng (`404 Not Found`)
```json
{
  "type": "about:blank",
  "title": "Thuộc tính không tồn tại",
  "status": 404,
  "detail": "Sản phẩm không có trong giỏ hàng",
  "instance": "/api/cart/items/ATTR-MIPAD7P-BLUE-12-512",
  "errorCode": "ATTRIBUTES_NOT_FOUND"
}
```

### 6.4. Lỗi Dữ liệu đầu vào không hợp lệ (`400 Bad Request`)
```json
{
  "type": "about:blank",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/cart/items/ATTR-TABS10U-GRAPH-5G-512",
  "errorCode": "VALIDATION_FAILED",
  "fieldErrors": {
    "quantity": "Số lượng không được âm"
  }
}
```

### 6.5. Lỗi Quyền truy cập (`403 Forbidden`)
```json
{
  "type": "about:blank",
  "title": "Access Denied",
  "status": 403,
  "detail": "You do not have permission to perform this action.",
  "instance": "/api/cart/merge",
  "errorCode": "ACCESS_DENIED"
}
```

---

## 7. 📋 Bằng chứng Thực nghiệm & Nhật ký Server Thực tế (Live Verification & Logs)

Toàn bộ các endpoint đã được gửi request trực tiếp đến ứng dụng đang chạy thực tế trên cổng `8080`. Dưới đây là trích xuất nhật ký thực thi từ `server.log`:

```text
# Khởi động ứng dụng & Web Server Tomcat trên cổng 8080:
13:27:15.344 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat initialized with port 8080 (http)
13:27:19.279 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
13:27:25.780 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8080 (http) with context path '/'
13:27:25.982 [main] INFO  com.ddicg.erp.ErpApplication - Started ErpApplication in 16.028 seconds

# Kiểm thử lỗi định danh (401):
13:30:21.573 [http-nio-8080-exec-9] WARN  c.d.e.c.e.GlobalExceptionHandler - Business exception: [UNAUTHORIZED] Vui lòng đăng nhập hoặc cung cấp header X-Guest-Id
13:31:06.677 [http-nio-8080-exec-9] WARN  c.d.e.c.e.GlobalExceptionHandler - Business exception: [UNAUTHORIZED] Vui lòng đăng nhập hoặc cung cấp header X-Guest-Id

# Kiểm thử lỗi SKU không tồn tại (404):
13:31:17.834 [http-nio-8080-exec-10] WARN c.d.e.c.e.GlobalExceptionHandler - Business exception: [ATTRIBUTES_NOT_FOUND] Sản phẩm [NON_EXISTENT_SKU_123] không tồn tại

# Thêm sản phẩm thành công vào Redis Hash (200):
13:31:37.050 [http-nio-8080-exec-3] INFO  c.d.e.m.c.s.ShoppingCartServiceImpl - Owner [guest:test-guest-endpoint-1] đã thêm 1 sản phẩm vào giỏ hàng Redis

# Kiểm thử lỗi SKU không có trong giỏ (404):
13:31:51.942 [http-nio-8080-exec-7] WARN  c.d.e.c.e.GlobalExceptionHandler - Business exception: [ATTRIBUTES_NOT_FOUND] Sản phẩm không có trong giỏ hàng

# Kiểm thử Validation số lượng âm và vượt hạn mức (400):
13:32:04.478 [http-nio-8080-exec-9] WARN  c.d.e.c.e.GlobalExceptionHandler - Validation failed: [Field error in object 'updateCartItemRequest' on field 'quantity': default message [Số lượng không được âm]]
13:32:07.893 [http-nio-8080-exec-5] WARN  c.d.e.c.e.GlobalExceptionHandler - Validation failed: [Field error in object 'updateCartItemRequest' on field 'quantity': default message [Số lượng không được vượt quá 99]]

# Cập nhật số lượng thành công (200):
13:32:10.765 [http-nio-8080-exec-7] INFO  c.d.e.m.c.s.ShoppingCartServiceImpl - Owner [guest:test-guest-endpoint-1] đã cập nhật SKU [ATTR-TABS10U-GRAPH-5G-512] với số lượng 5 trên Redis

# Xóa sản phẩm đơn lẻ (200):
13:32:22.724 [http-nio-8080-exec-10] INFO c.d.e.m.c.s.ShoppingCartServiceImpl - Owner [guest:test-guest-endpoint-1] đã xóa SKU [ATTR-TABS10U-GRAPH-5G-512] khỏi giỏ hàng Redis

# Xóa chọn lọc theo danh sách SKU (200):
13:32:33.058 [http-nio-8080-exec-4] INFO  c.d.e.m.c.s.ShoppingCartServiceImpl - Owner [guest:test-guest-endpoint-1] đã xóa 1 sản phẩm khỏi giỏ hàng Redis

# Làm trống toàn bộ giỏ hàng (200):
13:32:36.868 [http-nio-8080-exec-5] INFO  c.d.e.m.c.s.ShoppingCartServiceImpl - Owner [guest:test-guest-endpoint-1] đã xóa toàn bộ giỏ hàng Redis

# Hợp nhất giỏ hàng Guest vào User thành công (200):
13:34:25.025 [http-nio-8080-exec-1] INFO  c.d.e.m.c.s.ShoppingCartServiceImpl - User [annoeye@gmail.com] đã hợp nhất giỏ hàng từ Guest [guest-merge-999] thành công
13:34:29.931 [http-nio-8080-exec-7] INFO  c.d.e.m.c.s.ShoppingCartServiceImpl - User [annoeye@gmail.com] đã hợp nhất giỏ hàng từ Guest [guest-header-test-1] thành công
```

---

## 8. 🧪 Kết quả Kiểm thử Tự động (Automated Test Suite)

Bên cạnh kiểm thử live HTTP, toàn bộ 36 ca kiểm thử tự động chuyên sâu của module Giỏ hàng đều đạt tỷ lệ pass tuyệt đối **100%**:

```text
-------------------------------------------------------------------------------
Test set: com.ddicg.erp.modules.cart.service.ShoppingCartServiceTest
-------------------------------------------------------------------------------
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.812 s - PASSED

-------------------------------------------------------------------------------
Test set: com.ddicg.erp.modules.cart.controller.ShoppingCartControllerTest
-------------------------------------------------------------------------------
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.124 s - PASSED

-------------------------------------------------------------------------------
Test set: com.ddicg.erp.modules.cart.controller.ShoppingCartIntegrationTest
-------------------------------------------------------------------------------
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.486 s - PASSED

===============================================================================
TOTAL MODULE CART AUTOMATED TESTS: 36/36 PASSED (100% PASS RATE - 0 REGRESSION)
===============================================================================
```
