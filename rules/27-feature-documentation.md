# Rule: 27-feature-documentation

**File Path:** `rules/27-feature-documentation.md`

## Scope

Đặc tả nghiệp vụ và tài liệu hóa tính năng hệ thống (Feature & Business Contract Documentation).

Tài liệu này được thiết kế theo nguyên tắc: **Độc lập với cấu trúc mã nguồn nội bộ**, giúp lập trình viên ở các dự án khác, frontend, third-party hoặc AI agent có thể hiểu trọn vẹn nghiệp vụ, luồng xử lý và API contract mà không phụ thuộc vào chi tiết cài đặt ngôn ngữ (như Java annotations, framework classes hay cây thư mục source code).

---

## Trigger

Áp dụng khi:
- Người dùng yêu cầu viết hoặc cập nhật tài liệu cho một tính năng/module (`/write-feature-docs`, "viết doc cho feature X", "tài liệu hóa module Y").
- Một tính năng mới hoàn thành hoặc vừa được tái cấu trúc cần bàn giao đặc tả nghiệp vụ.

---

## 1. Associated Skills & Rules

- **Skills:**
  - **`write-feature-docs`**: Trích xuất luồng nghiệp vụ từ codebase, kiểm chứng tính chính xác và biên soạn tài liệu.
  - **`api-documentation-generator`**: Trích xuất hợp đồng giao tiếp API (Method, Path, Request/Response payloads, Validations).
  - **`obsidian-markdown`**: Định dạng cú pháp Markdown chuẩn Obsidian (Wikilinks, Callouts, Frontmatter, Bảng Markdown không lỗi vỡ giao diện).
- **Rules:**
  - **`user_global`**: Tuân thủ bằng chứng xác thực, không tự suy diễn phi logic.
  - **`rules/20-architecture-design.md`**: Tham chiếu kiến trúc tổng thể của tính năng.

---

## 2. Execution Protocol

### STEP_1: Khảo sát & Bóc tách Nghiệp Vụ từ Codebase
1. **Trích xuất mục tiêu nghiệp vụ (Business Goals):**
   - Phân tích chức năng giải quyết bài toán gì cho người dùng/hệ thống.
   - Xác định các khái niệm nghiệp vụ chính (Business Concepts & Models) thay vì ghi nhận code keywords (`@Embeddable`, `@Entity`, `@Column`).
2. **Khảo sát Hợp đồng Giao tiếp (API Contracts):**
   - Đọc controller và DTO để trích xuất chuẩn xác: Method, Path, Authentication Type, Request Payload (JSON), Response Payload (JSON) và các ràng buộc dữ liệu.
3. **Xác định các Bất Biến Nghiệp Vụ (Business Invariants):**
   - Vòng đời Token/Khóa (TTL, tái sử dụng, hủy sau khi dùng).
   - Quy tắc giới hạn tần suất/thời gian (Cooldown).
   - Thu hồi phiên làm việc (Session Revocation) sau khi thay đổi thông tin nhạy cảm.
   - Nguyên tắc bảo mật dữ liệu (Zero Client ID reliance - không tin cậy ID từ client).

---

### STEP_2: Soạn thảo Tài liệu theo Khung Chuẩn Nghiệp Vụ

Tài liệu phải tuân thủ nghiêm ngặt khung cấu trúc sau:

```markdown
---
module: "<module-name>"
status: "completed" # completed | in-progress | planned | draft
priority: "high"    # high | medium | low
description: "<Tóm tắt 1-2 câu về mục tiêu nghiệp vụ của tính năng>"
tags:
  - "feature/<feature-name>"
  - "module/<module-name>"
---

# <Tên Tính Năng / Nghiệp Vụ>

> [!NOTE]
> <Tóm tắt mục đích nghiệp vụ, phạm vi áp dụng và giá trị mang lại cho người dùng.>

---

## 1. Khái Niệm Cốt Lõi & Mô Hình Dữ Liệu (Core Concepts & Data Models)
<!-- Trình bày các khái niệm nghiệp vụ (ví dụ: Thông tin thiết bị, Mã xác thực, Kiểm toán dữ liệu).
Dùng bảng Markdown chuẩn và JSON ví dụ. Tuyệt đối không dùng code annotation như @Embeddable, @Entity. -->

## 2. Đặc Tả Chi Tiết Từng Chức Năng (API Specifications)
<!-- Cho mỗi chức năng: -->
### 2.X. <Tên Chức Năng>
* **Method & Path:** `<HTTP_METHOD> /api/...`
* **Xác thực:** `Public` | `Bearer JWT`
* **Request Payload:** <Mô tả các trường & ví dụ JSON thực tế>
* **Response Payload:** <Mô tả các trường & ví dụ JSON thực tế>
* **Quy tắc nghiệp vụ:** <Các điều kiện bắt buộc, xử lý token, cooldown>

## 3. Sơ Đồ Luồng Tuần Tự (Sequence Workflows)
<!-- Dùng Mermaid sequenceDiagram với participant tên ngắn gọn (UI, AuthAPI, Authorization, TokenStore, Session).
Không dùng tên class dài gây tràn chiều ngang khi xem trên Obsidian. -->

## 4. Bảng Xử Lý Lỗi Hệ Thống (HTTP Error Matrix)
<!-- Bảng Markdown chuẩn (không cách dòng thừa để tránh vỡ giao diện Obsidian):
| Tình huống lỗi | Mã HTTP | Error Message | Hành vi hệ thống |
| :--- | :---: | :--- | :--- |
| ... | ... | ... | ... |
-->
```

---

### STEP_3: Lưu Trữ & Kiểm Tra Tương Thích Obsidian
1. **Vị trí lưu file:** Lưu trực tiếp tại `<repository-root>/docs/features/<feature-name>/index.md`.
2. **Quy tắc tương thích giao diện Obsidian:**
   - **Bảng Markdown:** Viết liền mạch, căn chuẩn dấu gạch nối `| :--- | :---: |`, không chèn dòng trống giữa các dòng của bảng để tránh vỡ render thành text thô.
   - **Mermaid Diagrams:** Giữ số lượng participant từ 4–5 đối tượng với alias ngắn để sơ đồ vừa vặn trong khung đọc của Obsidian.
   - **Không chứa Codebase Index:** Không tạo mục danh sách file code nội bộ để tài liệu giữ nguyên tính độc lập nghiệp vụ.

---

## 3. Anti-Patterns (Những điều cấm kỵ)

- ❌ **Lẫn lộn chi tiết code vào tài liệu nghiệp vụ:** Đưa các từ khóa như `@Embeddable`, `@Entity`, `@Column`, `@PreAuthorize` vào nội dung thay vì dùng ngôn ngữ nghiệp vụ.
- ❌ **Mermaid Diagram quá cồng kềnh:** Đặt tên participant theo tên Java class dài dòng (`CredentialChangeAuthorization`) làm sơ đồ bị tràn khung và khó đọc trên mobile/Obsidian.
- ❌ **Lỗi cú pháp bảng Markdown:** Định dạng bảng lỏng lẻo làm vỡ giao diện hiển thị trong Obsidian Reading View.
- ❌ **Gắn chặt vào cây thư mục code:** Tạo mục liên kết file mã nguồn (`.java`) làm tài liệu mất tính khái quát độc lập.
