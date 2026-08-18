# 01 - DOMAIN MODEL AND DATA SCHEMAS

## 1. Domain Entities & Database Mapping

### 1.1 IAM Domain (`com.ddicg.erp.modules.iam.model`)

#### `User` Entity
| Column / Field | Java Type | JPA Mapping & Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` | Khóa chính tự tăng |
| `username` | `String` | `@Column(unique = true, nullable = false, length = 50)` | Tên đăng nhập duy nhất |
| `email` | `String` | `@Column(unique = true, nullable = false, length = 100)` | Email xác thực tài khoản |
| `password` | `String` | `@Column(nullable = false, length = 255)` | Hash mật khẩu (BCrypt) |
| `fullName` | `String` | `@Column(length = 100)` | Tên hiển thị đầy đủ |
| `avatarUrl` | `String` | `@Column(length = 500)` | Đường dẫn ảnh đại diện trên S3/MinIO |
| `status` | `UserStatus` | `@Enumerated(EnumType.STRING)` | `ACTIVE`, `INACTIVE`, `BLOCKED` |
| `roles` | `Set<Role>` | `@ManyToMany(fetch = EAGER)` | Danh sách quyền vai trò (`ROLE_ADMIN`, `ROLE_CUSTOMER`) |
| `lastUsernameChange` | `Instant` | `@Column` | Thời điểm đổi username gần nhất (Cooldown 30 ngày) |

#### `Address` Entity
| Column / Field | Java Type | JPA Mapping & Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` | Khóa chính |
| `userId` | `Long` | `@ManyToOne @JoinColumn(name = "user_id")` | Khóa ngoại trỏ về `User` |
| `recipientName`| `String` | `@Column(nullable = false, length = 100)` | Tên người nhận hàng |
| `phoneNumber`  | `String` | `@Column(nullable = false, length = 20)` | Số điện thoại liên hệ |
| `streetAddress`| `String` | `@Column(nullable = false, length = 255)` | Địa chỉ số nhà, tên đường |
| `city` / `ward`| `String` | `@Column(length = 100)` | Tỉnh / Thành phố, Phường / Xã |
| `isDefault`    | `boolean`| `@Column(nullable = false)` | Địa chỉ mặc định |

---

### 1.2 Merchandise Domain (`com.ddicg.erp.modules.merchandise.model`)

#### `Category` Entity
| Column / Field | Java Type | JPA Mapping & Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` | Khóa chính |
| `sku` | `String` | `@Column(unique = true, nullable = false)` | Mã SKU danh mục (vd `ctgr-8392`) |
| `name` | `String` | `@Column(nullable = false, length = 150)` | Tên danh mục |
| `parentId` | `Long` | `@Column` | ID danh mục cha (Cấu trúc cây) |
| `deletedAt` | `Instant` | `@Column` | Thời gian xóa mềm (Soft delete 30 ngày) |

#### `Product` Entity
| Column / Field | Java Type | JPA Mapping & Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` | Khóa chính |
| `sku` | `String` | `@Column(unique = true, nullable = false)` | Mã SKU mặt hàng chính (vd `prd-9281-92`) |
| `name` | `String` | `@Column(nullable = false, length = 255)` | Tên sản phẩm |
| `categoryId` | `Long` | `@Column(nullable = false)` | ID danh mục sản phẩm thuộc về |
| `images` | `List<String>` | `@ElementCollection` | Danh sách URL ảnh MinIO/S3 |
| `viewCount` | `Long` | `@Column(defaultValue = "0")` | Lượt xem |
| `soldCount` | `Long` | `@Column(defaultValue = "0")` | Lượt đã bán |
| `revenue` | `BigDecimal` | `@Column(precision = 19, scale = 2)` | Doanh thu tích lũy |

#### `Attributes` Entity (Biến thể SKU)
| Column / Field | Java Type | JPA Mapping & Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` | Khóa chính |
| `sku` | `String` | `@Column(unique = true, nullable = false)` | Mã biến thể bán hàng (vd `attr-92-4821`) |
| `productId` | `Long` | `@Column(nullable = false)` | ID mặt hàng gốc |
| `color` | `String` | `@Column(length = 50)` | Màu sắc (vd: Đỏ, Xanh) |
| `size` | `String` | `@Column(length = 50)` | Kích cỡ (vd: S, M, L, XL) |
| `importPrice` | `BigDecimal` | `@Column(precision = 19, scale = 2)` | Giá nhập kho |
| `listPrice` | `BigDecimal` | `@Column(precision = 19, scale = 2)` | Giá niêm yết (gốc) |
| `salePrice` | `BigDecimal` | `@Column(precision = 19, scale = 2)` | Giá bán thực tế |

#### `ProductInventory` Entity
| Column / Field | Java Type | JPA Mapping & Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | `@Id @GeneratedValue(strategy = IDENTITY)` | Khóa chính |
| `attributeSku` | `String` | `@Column(unique = true, nullable = false)` | Mã SKU biến thể |
| `quantity` | `Integer` | `@Column(nullable = false)` | Số lượng tồn kho thực tế |
| `version` | `Long` | `@Version` | Khóa lạc quan (Optimistic Locking) chống bán âm kho |

---

### 1.3 Order & Cart Domain (`com.ddicg.erp.modules.order` & `cart`)

#### `ShoppingCart` & `CartItem`
- `ShoppingCart`: `id`, `userId` (1-to-1 với User).
- `CartItem`: `id`, `cartId`, `attributeSku`, `quantity`, `unitPrice`, `addedAt`.

#### `Order` & `OrderItem`
- `Order`:
  - `id`: `String` / `UUID`.
  - `orderNumber`: `String` (Mã đơn hàng duy nhất vd: `ORD-20260816-9921`).
  - `userId`: `Long` (ID khách hàng).
  - `totalAmount`: `BigDecimal` (Tổng giá trị đơn hàng).
  - `status`: `OrderStatus` (`PENDING`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUNDED`).
  - `shippingAddress`: `CustomerInfo` (Tên, SĐT, Địa chỉ giao hàng snapshot).
  - `deliveryPin`: `String` (Mã PIN bảo mật cho shipper xác nhận giao hàng).
- `OrderItem`:
  - `id`, `orderId`, `attributeSku`, `productName`, `variantInfo`, `quantity`, `price` (Snapshot giá mua).
- `Payment`:
  - `id`, `orderId`, `paymentMethod` (`VNPAY`, `CASH`, `BANK_TRANSFER`), `transactionNo`, `amount`, `status` (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`).
