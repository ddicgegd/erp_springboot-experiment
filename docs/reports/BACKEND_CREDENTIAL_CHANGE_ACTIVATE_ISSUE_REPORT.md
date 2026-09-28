# BÁO CÁO KỸ THUẬT: LỆCH CHUẨN REST API & NGUYÊN TẮC PHẢN HỒI TẠI ENDPOINT `CREDENTIAL-CHANGE/ACTIVATE`

- **Hệ thống:** IAM & Notification Subsystem (`erp_springboot-experiment`)
- **Đối tượng:** `AuthController.java` & `AuthControllerImpl.java`
- **Mức độ nghiêm trọng:** **HIGH** (Gây vỡ hợp đồng REST API giữa Frontend SPA và Backend, vi phạm Content Negotiation và RFC 7807)
- **Ngày lập:** 2026-09-28
- **Trạng thái:** **RESOLVED** (Đã sửa đổi & kiểm thử thành công 100%)

---

## 1. Tóm Tắt Vấn Đề (Executive Summary)
Endpoint kích hoạt quyền thay đổi thông tin đăng nhập:
```http
GET /api/auth/credential-change/activate?token={token}
```
hiện tại đang **trả về trang Thymeleaf HTML (`text/html`) với mã HTTP `200 OK` trong mọi trường hợp (kể cả khi thất bại)**, thay vì trả về JSON envelope chuẩn REST API (`Response<String>`) hoặc RFC 7807 Problem Details như toàn bộ các API khác trong phân hệ IAM.

Điều này dẫn đến các hệ quả tiêu cực:
1. **Lệch pha hợp đồng (Contract Drift)**: Tài liệu đặc tả `CREDENTIAL_CHANGE_TOKEN_FLOW_DOCS.md` (Mục 3.2) cam kết trả về JSON, nhưng code thực tế lại trả về chuỗi HTML.
2. **Crash parser của Client/SPA**: Frontend gọi qua AJAX/Fetch (`apiRequest`) bị văng `SyntaxError: Unexpected token '<'` khi parse JSON từ phản hồi `<!DOCTYPE html>`.
3. **Mất tín hiệu mã lỗi HTTP (HTTP Status Masking)**: Khi token không hợp lệ hoặc hết hạn, controller nuốt exception và vẫn trả về HTTP `200 OK` dạng HTML, khiến các API client không thể bắt mã lỗi `401 Unauthorized` theo chuẩn HTTP.

---

## 2. Vị Trí Lỗi Trong Mã Nguồn (Code Evidence)

### Vị trí 1: `AuthControllerImpl.java` (Dòng 121–137)
```java
@Override
public ResponseEntity<String> activateCredentialToken(final String token) {
    Context ctx = new Context();
    try {
        userService.activateCredentialToken(token);
        ctx.setVariables(Map.of("success", true));
    } catch (BusinessException ex) {
        ctx.setVariables(Map.of(
            "success", false,
            "errorMessage", ex.getDetail()
        ));
    }
    String html = templateEngine.process("auth/credential-change-activate", ctx);
    return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
}
```

### Vị trí 2: Lệch pha với Đặc tả tại `CREDENTIAL_CHANGE_TOKEN_FLOW_DOCS.md` (Mục 3.2)
Tài liệu quy định:
- **Response Thành Công (`200 OK` - JSON):**
  ```json
  {
    "status": {
      "code": 200,
      "message": "Kích hoạt quyền thay đổi thành công. Bạn có 5 phút để cập nhật."
    },
    "data": "Kích hoạt quyền thay đổi thành công. Bạn có 5 phút để hoàn tất cập nhật."
  }
  ```
- **Response Thất Bại (`401 Unauthorized` - RFC 7807 JSON):**
  ```json
  {
    "type": "https://api.erp.annoeye.com/errors/invalid-credentials",
    "title": "Invalid Credentials",
    "status": 401,
    "detail": "Liên kết xác thực không hợp lệ hoặc đã hết hạn."
  }
  ```

---

## 3. Phân Tích Các Khiếm Khuyết Kỹ Thuật

| # | Vấn đề | Chi tiết |
|---|---|---|
| **1** | **Bỏ qua Content Negotiation** | Client gửi header `Accept: application/json`, nhưng controller ép cứng `.contentType(MediaType.TEXT_HTML)`, phớt lờ mong muốn định dạng dữ liệu của client. |
| **2** | **Xóa nhòa mã lỗi HTTP (HTTP Status Masking)** | Khi `userService.activateCredentialToken(token)` ném ra `BusinessException(ErrorCode.INVALID_CREDENTIALS)`, controller bắt lại (`catch`) và trả về `ResponseEntity.ok()` (`200 OK`) chứa HTML lỗi. Điều này vi phạm nguyên tắc REST và cơ chế xử lý lỗi tập trung `GlobalExceptionHandler`. |
| **3** | **Nhầm lẫn vai trò giữa Web Server & REST API Gateway** | Link gửi trong email thực tế trỏ về Frontend SPA (`http://localhost:3000/credential-change/activate?token=...`). Frontend SPA sau đó mới gọi AJAX xuống Backend API để xác thực. Việc Backend nhồi nguyên 1 trang web Thymeleaf có CSS CDN, script countdown vào payload API là thừa thãi và sai kiến trúc tách biệt Frontend/Backend. |

---

## 4. Phương Án Khắc Phục Đề Xuất Cho Agent Backend

### Phương án Tối Ưu (Khuyến nghị): Chuẩn Hóa RESTful Toàn Phần
Đưa `activateCredentialToken` về chuẩn chung của các IAM Controller khác:

1. **Sửa Interface `AuthController.java`**:
   ```java
   @GetMapping(value = "/credential-change/activate", produces = MediaType.APPLICATION_JSON_VALUE)
   @ResponseStatus(HttpStatus.OK)
   Response<String> activateCredentialToken(@RequestParam("token") final String token);
   ```

2. **Sửa Implementation `AuthControllerImpl.java`**:
   - Bỏ render Thymeleaf `templateEngine.process(...)`.
   - Để `BusinessException` tự do ném ra ngoài để `GlobalExceptionHandler` bắt và serialize thành RFC 7807 JSON (`401 Unauthorized`).
   ```java
   @Override
   public Response<String> activateCredentialToken(final String token) {
       return userService.activateCredentialToken(token);
   }
   ```

3. **(Tùy chọn) Hỗ trợ chuyển hướng nếu người dùng truy cập trực tiếp URL API trên trình duyệt**:
   Sử dụng Content Negotiation:
   - Nếu `Accept` header chứa `text/html`: Chuyển hướng về Frontend SPA:
     ```java
     return ResponseEntity.status(HttpStatus.FOUND)
             .location(URI.create(frontendUrl + "/credential-change/activate?token=" + token))
             .build();
     ```
   - Nếu `Accept` header chứa `application/json` (gọi từ SPA qua Fetch/AJAX): Trả về `Response<String>` JSON envelope chuẩn.

---

## 5. Kết Quả Xử Lý & Xác Minh (Resolution & Verification)

### 5.1. Các thay đổi đã thực hiện:
1. **`AuthController.java`**:
   - Sửa kiểu trả về của `activateCredentialToken`:
     ```java
     @GetMapping(value = "/credential-change/activate", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
     @ResponseStatus(HttpStatus.OK)
     Response<String> activateCredentialToken(@RequestParam("token") final String token);
     ```
2. **`AuthControllerImpl.java`**:
   - Xóa bỏ việc inject và gọi `TemplateEngine`, `Context`, `MediaType.TEXT_HTML`.
   - Xóa bỏ khối `try-catch` nuốt exception. Ủy quyền trực tiếp cho `userService.activateCredentialToken(token)`.
   - Ngoại lệ `BusinessException` được ném tự nhiên để `GlobalExceptionHandler` bắt và chuyển hóa thành RFC 7807 JSON với HTTP `401 Unauthorized`.
3. **Dọn dẹp mã nguồn thừa (Dead Code)**:
   - Đã xóa bỏ file template HTML `src/main/resources/templates/auth/credential-change-activate.html`.
4. **Cập nhật Unit Test (`AuthControllerRecoveryTest.java`)**:
   - Kiểm tra thành công trả về `Response<String>` với HTTP `200 OK`.
   - Kiểm tra khi token không hợp lệ hoặc hết hạn: Ném `BusinessException` với `ErrorCode.INVALID_CREDENTIALS`.

### 5.2. Kết quả kiểm thử:
- Toàn bộ **62 tests** thuộc IAM và Notification Module pass 100% không có lỗi.
- Đảm bảo hợp đồng RESTful JSON chuẩn giữa Frontend SPA và Backend.
