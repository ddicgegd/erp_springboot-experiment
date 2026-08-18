# 03 - MODULE MERCHANDISE API DOCUMENTATION

## 1. Overview & Scope
Phân hệ Hàng hóa (Merchandise) quản lý cấu trúc dữ liệu 3 tầng: **Category (Danh mục) $\rightarrow$ Product (Mặt hàng chính) $\rightarrow$ Attributes (Biến thể SKU Item)**, kèm theo cơ chế upload hình ảnh MinIO/S3, bộ lọc tìm kiếm nâng cao (Smart Search) và quản lý tồn kho đa biến thể.

- **Base Path:** `/api/merchandise`
- **Authentication:** Public cho xem/tìm kiếm sản phẩm; `ROLE_ADMIN` cho thao tác thêm, sửa, xóa.

---

## 2. API Endpoints Specification

### 2.1 Category Management

#### Create Category
- **HTTP Method:** `POST`
- **Path:** `/api/merchandise/add-Category`
- **Request Body (`CreateCategoryRequest`):**
```json
{
  "name": "Thời Trang Nam",
  "parentId": null // null nếu là danh mục gốc
}
```
- **Success Response (`201 Created`):** `Response<CategoryDto>`

#### Search Category Tree
- **HTTP Method:** `POST`
- **Path:** `/api/merchandise/search-Category`
- **Features:** Tích hợp Redis Caching `categoryDetails`.
- **Request Body (`CategorySearchRequest`):**
```json
{
  "keyword": "Thời Trang",
  "pageNumber": 0,
  "pageSize": 20
}
```

#### Soft Delete Category
- **HTTP Method:** `POST`
- **Path:** `/api/merchandise/delete-Category`
- **Request Body (`DeleteBySkusRequest`):** `{"skus": ["ctgr-8392"]}`
- **Business Rule:** Danh mục được đánh dấu `deletedAt = NOW()`, giữ lại 30 ngày trước khi dọn dẹp vĩnh viễn.

---

### 2.2 Product Management

#### Create Product with Images
- **HTTP Method:** `POST`
- **Path:** `/api/merchandise/add-Product`
- **Request Body (`CreateProductRequest`):**
```json
{
  "name": "Áo Thun Nam Cổ Tròn Cotton 100%",
  "categorySku": "ctgr-8392",
  "description": "Chất liệu cotton thoáng mát, thấm hút mồ hôi tốt."
}
```
- **Upload Product Images:** `POST /api/merchandise/add-Product-Images/{sku}` (Multipart file list).

#### Search Products (Smart Search Engine)
- **HTTP Method:** `POST`
- **Path:** `/api/merchandise/search-Product`
- **Request Body (`GetProductRequest`):**
```json
{
  "keyword": "Áo Thun",
  "categorySku": "ctgr-8392",
  "minPrice": 100000,
  "maxPrice": 500000,
  "inStock": true,
  "pageNumber": 0,
  "pageSize": 20,
  "sortBy": "soldCount",
  "sortDirection": "DESC"
}
```
- **Success Response (`200 OK`):** `Response<PagingResponse<ProductDto>>`

---

### 2.3 Attributes & SKU Variants Management

#### Create Attributes (Biến thể SKU)
- **HTTP Method:** `POST`
- **Path:** `/api/merchandise/add-Attributes`
- **Request Body (`CreateAttributesRequest`):**
```json
{
  "productSku": "prd-9281-92",
  "variants": [
    {
      "color": "Trắng",
      "size": "L",
      "quantity": 100,
      "importPrice": 80000,
      "listPrice": 180000,
      "salePrice": 149000
    },
    {
      "color": "Đen",
      "size": "XL",
      "quantity": 50,
      "importPrice": 80000,
      "listPrice": 180000,
      "salePrice": 149000
    }
  ]
}
```
- **Success Response (`201 Created`):** `Response<List<AttributesDto>>` với các mã SKU biến thể được sinh tự động (vd `attr-92-4821`).

#### Update SKU Inventory & 3-Tier Prices
- **HTTP Method:** `PUT`
- **Path:** `/api/merchandise/update-Attributes`
- **Request Body (`UpdateAttributesRequest`):** Cập nhật giá bán, số lượng tồn kho và thông tin thuộc tính.
